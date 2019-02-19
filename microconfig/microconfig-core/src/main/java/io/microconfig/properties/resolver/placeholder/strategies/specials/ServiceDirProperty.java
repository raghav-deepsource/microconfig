package io.microconfig.properties.resolver.placeholder.strategies.specials;

import io.microconfig.environments.Component;
import io.microconfig.environments.Environment;
import io.microconfig.properties.resolver.placeholder.strategies.SpecialPropertyResolverStrategy.SpecialProperty;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.util.Optional;

import static java.util.Optional.of;

@RequiredArgsConstructor
public class ServiceDirProperty implements SpecialProperty {
    private final File destinationComponentDir;

    @Override
    public String key() {
        return "serviceDir";
    }

    @Override
    public Optional<String> value(Component component, Environment environment) {
        return of(new File(destinationComponentDir, component.getName()).getAbsolutePath());
    }
}