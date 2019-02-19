package io.microconfig.properties.resolver.special;

import io.microconfig.environments.*;
import io.microconfig.properties.PropertiesProvider;
import io.microconfig.properties.Property;
import io.microconfig.properties.files.provider.ComponentTree;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.microconfig.properties.Property.Source.systemSource;
import static io.microconfig.utils.FileUtils.userHomeString;
import static io.microconfig.utils.StringUtils.unixLikePath;
import static java.util.Arrays.asList;

@RequiredArgsConstructor
public class SpecialPlaceholdersPropertiesProvider implements PropertiesProvider {
    private static final String PORT_OFFSET = "portOffset";
    private static final String IP = "ip";
    private static final String GROUP = "group";
    private static final String ORDER = "order";
    private static final String NAME = "name";
    private static final String ENV = "env";
    private static final String FOLDER = "folder";
    private static final String USER_HOME = "userHome";
    private static final String CONFIG_DIR = "configDir";
    private static final String SERVICE_DIR = "serviceDir";
    private static final List<String> ALL = asList(PORT_OFFSET, IP, ENV, NAME, GROUP, USER_HOME, CONFIG_DIR, SERVICE_DIR, FOLDER);

    private final PropertiesProvider delegate;
    private final EnvironmentProvider environmentProvider;
    private final ComponentTree componentTree;
    private final File destinationComponentDir;

    public static boolean isSpecialProperty(String name) {
        return ALL.contains(name);
    }

    @Override
    public Map<String, Property> getProperties(Component component, String environment) {
        Map<String, Property> properties = delegate.getProperties(component, environment);
        addEnvSpecificProperties(component, environment, properties);
        return properties;
    }

    private void addEnvSpecificProperties(Component component, String envName, Map<String, Property> properties) {
        Environment environment = getEnv(envName);
        if (environment == null) return;

        addPortOffset(properties, environment);
        addEnv(properties, environment);
        addName(properties, component, environment);
        addGroupProps(properties, component, environment);
        addConfigDir(properties, component, environment);
        addUserHome(properties, environment);
    }

    private void addPortOffset(Map<String, Property> properties, Environment environment) {
        environment.getPortOffset()
                .ifPresent(p -> properties.putIfAbsent(PORT_OFFSET, new Property(PORT_OFFSET, p.toString(), environment.getName(), systemSource(), true)));
    }

    private void addEnv(Map<String, Property> properties, Environment environment) {
        doAdd(ENV, environment.getName(), properties, environment, false);
    }

    private void addName(Map<String, Property> properties, Component component, Environment environment) {
        doAdd(NAME, component.getName(), properties, environment, false);
    }

    private void addGroupProps(Map<String, Property> properties, Component component, Environment environment) {
        Optional<ComponentGroup> group = environment.getComponentGroupByComponentName(component.getName());
        group.ifPresent(cg -> {
            int componentOrder = 1 + cg.getComponentNames().indexOf(component.getName());
            doAdd(ORDER, String.valueOf(componentOrder), properties, environment, true);
            doAdd(GROUP, cg.getName(), properties, environment, true);
            doAdd(SERVICE_DIR, new File(destinationComponentDir, component.getName()).getAbsolutePath(), properties, environment, true);
        });

        group.flatMap(ComponentGroup::getIp)
                .ifPresent(ip -> doAdd(IP, ip, properties, environment, true));
    }

    private void addConfigDir(Map<String, Property> properties, Component component, Environment environment) {
        String configDir = componentTree.getRepoDirRoot().getParentFile().getAbsolutePath();
        doAdd(CONFIG_DIR, unixLikePath(configDir), properties, environment, true);

        componentTree.getFolder(component.getType())
                .ifPresent(file -> doAdd(FOLDER, file.getAbsolutePath(), properties, environment, true));
    }

    private void addUserHome(Map<String, Property> properties, Environment environment) {
        doAdd(USER_HOME, unixLikePath(userHomeString()), properties, environment, true);
    }

    private void doAdd(String name, String value, Map<String, Property> properties, Environment environment, boolean temp) {
        properties.put(name, new Property(name, value, environment.getName(), systemSource(), temp));
    }

    private Environment getEnv(String envName) {
        try {
            return environmentProvider.getByName(envName);
        } catch (EnvironmentNotExistException e) {
            return null;
        }
    }
}