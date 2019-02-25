package io.microconfig.configs.files.io;

import io.microconfig.configs.Property;

import java.util.List;
import java.util.Map;

public interface ConfigReader {
    List<Property> properties();

    Map<String, String> propertiesAsMap();

    List<String> comments();
}