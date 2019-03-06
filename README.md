# Microconfig overview and features

[![Build Status](https://travis-ci.com/microconfig/microconfig.svg?branch=master)](https://travis-ci.com/microconfig/microconfig)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Microconfig is intended to make it easy and convenient to manage configuration for microservices (or just for a big amount of services) and reuse common part.

If your project consists of tens or hundreds of services you have to:

Keep configuration for each service, ideally separately from code.
* Configuration for different services can have common and specific parts. Also, the configuration for the same service in different environments can have common and specific parts as well.
* Common part for different services (or for one service in different environments) should not be copy-pasted and must be easy to reuse.
* It must be easy to understand how the result file is generated and based on what placeholders are resolved.
* Some configuration properties must be dynamic, calculated using an expression language.

Microconfig is written in Java, but it designed to be used with systems written in any language. Microconfig just describes format of base configuration, syntax for placeholders, includes, excludes, overrides, expression language for dynamic properties and engine than can build it to plain *.yaml or *.properties. Also it can resolve placeholders in arbitrary template files and show diff between config releases.

# Difference between Microconfig and popular devops tools
**Comparing to config servers** (like Spring cloud config server or Zookeeper):

Config servers solve the problem of dynamic distribution of configuration in runtime (can use http api endpoints), but to distribute configuration you have to store it, ideally with change history and without duplication of common part.

**Comparing to Ansible**:

Ansible is a powerful but too general engine for deployment management and doesn't provide a common and clean way to store configuration for microservices. And a lot of teams have to invent their own solutions based on Ansible.

Microconfig does one thing and does it well. It provides an approach, best practices how to keep configuration for a big amount of services and engine to build config sources into result files.

You can use Microconfig together with config servers and deployment frameworks. Configuration can be built during deploy phase and the resulting plain config files can be copied to the filesystem, where your services can access it directly(for instance, Spring Boot can read configuration from *.yaml or *.properties), or you can distribute result configuration using any config servers. Also, you can store not only application configuration but configuration how to run your services. And deployments frameworks can read configuration from Microconfig to start your services with right params and settings.

# Where to store configuration
It’s a good practice to keep service configuration separated from code. It allows not to rebuild your services any time configuration is changed and use the same service artifacts (for instance, *.jar) for all environments because it doesn’t contain any env specific configuration. Configuration can be updated even in runtime without service' source code changes.

So the best way to follow this principle is to have a dedicated repository for configuration in your favorite version control system.  You can store configuration for all microservices in the same repository to make it easy to reuse a common part and be sure the common part is consistent for all your services.

# Basic folder layout
Let’s see a basic folder layout that you can keep in a dedicated repository.

For every service, you have to create a folder with a unique name(name of the service). In the service directory, we will keep common and env specific configuration.

So let’s imagine we have 4 microservices: order service, payment service,  service-discovery, and api-gateway. For convenience we can group services by layers: 'infra' for infrastructure services and 'core' for our business domain services. The resulting layout will look like:

```
repo
└───core  
│    └───orders
│    └───payments
│	
└───infra
    └───service-discovery
    └───api-gateway
```

# Service configuration types

It's convenient to have different kinds of configuration and keep it in different files:
* Process configuration (the configuration used by deployment tools to start your service, like memory limit, VM params, etc.
* Application configuration (the configuration that your service reads after startup and use in runtime)
* OS ENV variables
* Lib specific templates (for instance, your logger specific descriptor (logback.xml), kafka.conf, cassandra.yaml, etc)
* Static files/scripts to run before/after your service start
* Secrets configuration (Note, you should not store in VCS any sensitive information, like passwords. In VCS you can store references(keys) to passwords, and keep passwords in special secured stores(like Vault) or at least in encrypted files on env machines)

# Service configuration files

Inside the service folder, you can create a configuration in key=value format.

Let’s create basic application and process configuration files for each service.
You can split configuration among several files, but for simplicity, we will create single application.properties and process.proc for each service. Anyway, after configuration build for each service for each config type, a single result file will be generated despite the number of base source files.

```
repo
└───core  
│    └───orders
│    │   └───application.properties
│    │   └───process.proc
│    └───payments
│        └───application.properties
│        └───process.proc
│	
└───infra
    └───service-discovery
    │   └───application.properties
    │   └───process.proc
    └───api-gateway
        └───application.properties
        └───process.proc
```

Inside process.proc we will store configuration that describes what is your service and how to run it (Your config files can have other properties, so don't pay attention to concrete values).

**orders/process.proc**
```*.properties
    artifact=org.example:orders:19.4.2 # artifact in maven format groupId:artifactId:version
    java.main=org.example.orders.OrdersStarter # main class to run
    java.opts.mem=-Xms1024M -Xmx2048M -XX:+UseG1GC -XX:+PrintGCDetails -Xloggc:logs/gc.log # vm params
```
**payments/process.proc**
```*.properties
    artifact=org.example:payments:19.4.2 # partial duplication
    java.main=org.example.payments.PaymentStarter
    java.opts.mem=-Xms1024M -Xmx2048M -XX:+UseG1GC -XX:+PrintGCDetails -Xloggc:logs/gc.log # duplication
    instance.count=2
```
**service-discovery/process.proc**
```*.properties
    artifact=org.example.discovery:eureka:19.4.2 # partial duplication         
    java.main=org.example.discovery.EurekaStarter
    java.opts.mem=-Xms1024M -Xmx2048M # partial duplication         
```

As you can see we already have some small copy-paste (all services have 19.4.2 version, two of them have the same java.ops params).  Configuration duplication is as bad as code one. We will see further how to do it better.

Let's see how application properties can look like. In comments we note what can be improved.

**orders/application.properties**
```*.properties
    server.port=9000
    application.name=orders # better to get name from folder
    orders.personalRecommendation=true
    statistics.enableExtendedStatistics=true
    service-discovery.url=http://10.12.172.11:6781 # are you sure url is consistent with SD configuration?
    eureka.instance.prefer-ip-address=true  # duplication        
    datasource.minimum-pool-size=2  # duplication
    datasource.maximum-pool-size=10
    datasource.url=jdbc:oracle:thin:@172.30.162.31:1521:ARMSDEV  # partial duplication
    jpa.properties.hibernate.id.optimizer.pooled.prefer_lo=true  # duplication
```
**payments/application.properties**
```*.properties
    server.port=8080
    application.name=payments # better to get name from folder
    payments.booktimeoutInMs=900000 # how long in min ?
    payments.system.retries=3
    consistency.validateConsistencyIntervalInMs=420000 # difficult to read. how long in min ?
    service-discovery.url=http://10.12.172.11:6781 # are you sure url is consistent with eureka configuration?
    eureka.instance.prefer-ip-address=true  # duplication            
    datasource.minimum-pool-size=2  # duplication
    datasource.maximum-pool-size=5    
    datasource.url=jdbc:oracle:thin:@172.30.162.127:1521:ARMSDEV  # partial duplication
    jpa.properties.hibernate.id.optimizer.pooled.prefer_lo=true # duplication
```
**service-discovery/application.properties**
```*.properties
    server.port=6781
    application.name=eureka
    eureka.client.fetchRegistry=false
    eureka.server.eviction-interval-timer-in-ms=15000 # difficult to read
    eureka.server.enable-self-preservation=false    
```

The first bad thing - application files contain duplication. 
Also, you have to spend some time to understand the application’s dependencies or its structure. 
For instance, payments service contains settings for:
*  service-discovery client
* oracle db 
* application specific 

Of course, you can separate a group of settings by an empty line. But we can do it more readable and understandable.

# Better config structure using #include
Our services have a common configuration for service-discovery and database. To make it easy to understand service's dependencies, let’s create folders for service-discovery-client and oracle-client and specify links to these dependencies from core services.

```
repo
└───common
|    └───service-discovery-client 
|    | 	 └───application.properties
|    └───oracle-client
|        └───application.properties
|	
└───core  
│    └───orders
│    │   ***
│    └───payments
│        ***
│	
└───infra
    └───service-discovery
    │   ***
    └───api-gateway
        ***
```
**service-discovery-client/application.properties**
```*.properties
service-discovery.url=http://10.12.172.11:6781 # are you sure url is consistent with eureka configuration?
eureka.instance.prefer-ip-address=true 
```

**oracle-client/application.properties**
```*.properties
datasource.minimum-pool-size=2  
datasource.maximum-pool-size=5    
datasource.url=jdbc:oracle:thin:@172.30.162.31:1521:ARMSDEV  
jpa.properties.hibernate.id.optimizer.pooled.prefer_lo=true
```

And replace explicit configs with includes

**orders/application.properties**
```*.properties
    #include service-discovery-client
    #include oracle-db-client
    
    server.port=9000
    application.name=orders # better to get name from folder
    orders.personalRecommendation=true
    statistics.enableExtendedStatistics=true    
```

**payments/application.properties**
```*.properties
    #include service-discovery-client
    #include oracle-db-client
    
    server.port=8080
    application.name=payments # better to get name from folder
    payments.booktimeoutInMs=900000 # how long in min ?
    payments.system.retries=3
    consistency.validateConsistencyIntervalInMs=420000 # difficult to read. how long in min ?    
```

Some problems still here, but we removed duplication and made it easy to understand the service's dependencies.

You can override any properties from your dependencies.
Let's override order's connection pool size.

**orders/application.properties**
```*.properties        
    #include oracle-db-client
    datasource.maximum-pool-size=10
    ***    
```

Nice. But order-service has a small part of its db configuration(pool-size), it's not that bad, but we can make the config semantically better.
Also as you could notice order and payment services have different ip for oracle.

order: datasource.url=jdbc:oracle:thin:@172.30.162.<b>31</b>:1521:ARMSDEV  
payment: datasource.url=jdbc:oracle:thin:@172.30.162.<b>127</b>:1521:ARMSDEV  
And oracle-client contains settings for .31.

Of course, you can override datasource.url in payment/application.properties. But this overridden property will contain a duplication of another part of jdbc url and you will get all standard copy-paste problems. We would like to override only a part of the property. 

Also it better to create a dedicated configuration for order db and payment db. Both db configuration will include common-db config and override ip part of url.  After that, we will migrate datasource.maximum-pool-size from orders service to order-db, so order service will contain only links to its dependencies and service-specific configs.

Let’s refactor.
```
repo
└───common
|    └───oracle
|        └───oracle-common
|        |   └───application.properties
|        └───order-db
|        |   └───application.properties
|        └───payment-db
|            └───application.properties
```

**oracle-common/application.properties**
```*.properties
datasource.minimum-pool-size=2  
datasource.maximum-pool-size=5    
connection.timeoutInMs=300000
jpa.properties.hibernate.id.optimizer.pooled.prefer_lo=true
```
**orders-db/application.properties**
```*.properties
    #include oracle-common
    datasource.maximum-pool-size=10
    datasource.url=jdbc:oracle:thin:@172.30.162.31:1521:ARMSDEV #partial duplication
```
**payment-db/application.properties**
```*.properties
    #include oracle-common
    datasource.url=jdbc:oracle:thin:@172.30.162.127:1521:ARMSDEV #partial duplication
```

**orders/application.properties**
```*.properties
    #include order-db
    ***
```

**payments/application.properties**
```*.properties
    #include payment-db
```


Also includes can be in one line:

**payments/application.properties**
```*.properties
    #include service-discovery-client, oracle-db-client    
```

# Env specific properties
Microconfig allows specifying env specific properties (add/remove/override). For instance, you want to increase connection-pool-size for dbs and increase the amount of memory for prod env.
To add/remove/override properties for env, you can create application.**${ENVNAME}**.properties file in the config folder. 

Let's override connection pool connection size for dev and prod and add one new param for dev. 

```
order-db
└───application.properties
└───application.dev.properties
└───application.prod.properties
```

**orders-db/application.dev.properties**
```*.properties   
    datasource.maximum-pool-size=15    
```

**orders-db/application.prod.properties**
```*.properties   
    datasource.maximum-pool-size=50    
```

Also, you can declare common properties for several environments on a single file.  You can use the following file name pattern: application.**${ENV1.ENV2.ENV3...}**.properties
Let's create common props for dev, dev2 and test envs.

```
order-db
└───application.properties
└───application.dev.properties
└───application.dev.dev2.test.properties
└───application.prod.properties
```

**orders-db/application.dev.dev2.test.properties**
```*.properties   
    hibernate.show-sql=true    
```

When you build properties for specific env (for example 'dev') Microconfig will collect properties from:
* application.properties 
* then add/override properties from application.dev.{anotherEnv}.properties.
* then add/override properties from application.dev.properties.

# Placeholders

Instead of copy-paste value of some property, Microconfig allows to have a link (placeholder) to this value. 

Let's refactor service-discovery-client config.

Initial:

**service-discovery-client/application.properties**
```*.properties
service-discovery.url=http://10.12.172.11:6781 # are you sure host and port are consistent with SD configuration? 
```
**service-discovery/application.properties**
```*.properties
server.port=6761 
```

Refactored:

**service-discovery-client/application.properties**
```*.properties
service-discovery.url=http://${service-discovery@ip}:${service-discovery@server.port}
```
**service-discovery/application.properties**
```*.properties
server.port=6761
ip=10.12.172.11 
```
So if you change service-discovery port, all dependent services will get this update.

Microconfig has another approach to store service's ip. We will discuss it later. For now, it's better to set ip property inside service-discovery config file. 

Microconfig syntax for placeholders ${**componentName**@**propertyName**}. Microconfig forces to specify component name(folder). This syntax match better than just prop name 
(like ${serviceDiscoveryPortName}), because it makes it obvious based on what placeholder will be resolved and where to find initial placeholder value.

Let's refactor oracle db config using placeholders and env specific overrides.

Initial:

**oracle-common/application.properties**
```*.properties    
    datasource.maximum-pool-size=10
    datasource.url=jdbc:oracle:thin:@172.30.162.31:1521:ARMSDEV 
```   

Refactored:

**oracle-common/application.properties**
```*.properties    
    datasource.maximum-pool-size=10
    datasource.url=jdbc:oracle:thin:@${this@oracle.host}:1521:${this@oracle.sid}
    oracle.host=172.30.162.20    
    oracle.sid=ARMSDEV
```
**oracle-common/application.uat.properties**
```*.properties    
    oracle.host=172.30.162.80
```    
    
**oracle-common/application.prod.properties**
```*.properties    
    oracle.host=10.17.14.18    
    oracle.sid=ARMSPROD    
```        

As you can see using placeholders we can override not the whole property but only a part of it. 

If you want to declare temp properties that will be used for placeholders and you don't want them to be included in the result config file, you can declare them with #var keyword.

**oracle-common/application.properties**
```*.properties
    datasource.url=jdbc:oracle:thin:@${this@oracle.host}:1521:${this@oracle.sid}
    #var oracle.host=172.30.162.20    
    #var oracle.sid=ARMSDEV
```
**oracle-common/application.uat.properties**
```*.properties    
    #var oracle.host=172.30.162.80
``` 

This approach works with includes as well. You can #include oracle-common and then override oracle.host, and datasource.url will be resolved based on overridden value.

In the example below after build datasource.url=jdbc:oracle:thin:@**100.30.162.80**:1521:ARMSDEV
 
**orders-db/application.dev.properties** 
```*.properties   
     #include oracle-common    
     #var oracle.host=100.30.162.80                 
```  

Placeholder can link to another placeholder. Microconfig can resolve them recursively and detect cyclic dependencies.

# Placeholder's default value
You can specify a default value for placeholder using syntax ${component@property:**defaultValue**}

Let's set default value for oracle host

**oracle-common/application.properties**
```*.properties    
    datasource.maximum-pool-size=10
    datasource.url=jdbc:oracle:thin:@${this@oracle.host:172.30.162.20}:1521:${this@oracle.sid}        
    #var oracle.sid=ARMSDEV
```
Note, a default value can be a placeholder:
 ${component@property:${component2@property7:Missing value}}
 
Microconfig will try to:
* resolve `${component@property}`
* if it's missing - resolve `${component2@property7}`
* if it's missing - return 'Missing value'

If a placeholder doesn't have a default value and that placeholder can't be resolved Microconfig throws an exception with the detailed problem description.    

# Removing  base properties
Using #var you can remove properties from the result config file. You can include some config and override any property with #var to exclude it from the result config file. 

Let's remove 'payments.system.retries' property for dev env:

**payments/application.properties**
```*.properties
    payments.system.retries=3        
```
**payments/application.dev.properties**
```*.properties
    #var payments.system.retries=  // will not be included into result config        
```
# Specials placeholders
As we discussed syntax for placeholders looks like `${component@property}`.
Microconfig has several special useful placeholders:

* ${this@env} - returns current env name 
* ${...@name} - returns component's config folder name
* ${...@folder} - returns full path of component's config dir 
* ${this@configDir} - returns full path of root config dir   
* ${...@serviceDir} - returns full path of destination service dir (result files will be put into this dir)

There are some other env descriptor related properties, we will discuss them later:
* ${...@portOffset}
* ${...@ip}
* ${...@group}
* ${...@order}

Note, if you use special placeholders with ${this@...} than value will be context dependent. Let's apply ${this@name} to see why it's useful.

Initial:

**orders/application.properties**
```*.properties
    #include service-discovery-client    
    application.name=orders    
```
**payments/application.properties**
```*.properties
    #include service-discovery-client    
    application.name=payments
```

Refactored:

**orders/application.properties**
```*.properties
    #include service-discovery-client    
```
**payments/application.properties**
```*.properties
    #include service-discovery-client
```
**service-discovery-client/application.properties**
```*.properties            
    application.name=${this@name}
```                 

# Env variables and system properties 
To resolve env variables use the following syntax: `${env@variableName}`

For example:
```
 ${env@Path}
 ${env@JAVA_HOME}
 ${env@NUMBER_OF_PROCESSORS}
```

To resolve Java system variables (System::getProperty) use the following syntax: `${system@variableName}`

Some useful standard system variables:

```
 ${system@user.home}
 ${system@user.name}
 ${system@os.name}
```

You can pass your own system properties during Microconfig start with -D prefix (See 'Running config build' section)

Example:
```
 -DtaskId=3456 -DsomeParam3=value
```
Then you can access it: `${system@taskId}` or `${system@someParam3}`
 
# Profiles and explicit env name for includes and placeholders
As we discussed you can create env specific properties using filename pattern: application.${ENV}.properties. You can use the same approach for creating profile specific properties.

For example, you can create a folder for http client timeout settings:

**timeout-settings/application.properties**
```*.properties    
    timeouts.connectTimeoutMs=1000    
    timeouts.readTimeoutMs=5000    
```
And some services can include this configuration:

**orders/application.properties**
```*.properties
    #include timeout-settings    
```
**payments/application.properties**
```*.properties
    #include timeout-settings
```

But what if you want some services to be configured with long timeout? Instead of env you can use profile name in the filename:
```
timeout-settings
└───application.properties
└───application.long.properties
└───application.huge.properties
```
**timeout-settings/application.long.properties**
```*.properties
    timeouts.readTimeoutMs=30000    
```
**timeout-settings/application.huge.properties**
```*.properties
    timeouts.readTimeoutMs=600000    
```

And specify profile name with include:

**payments/application.properties**
```*.properties
    #include timeout-settings[long]
```

You can use profile/env name with placeholders as well:

```
${timeout-settings[long]@readTimeoutMs}
${kafka[test]@bootstrap-servers}
```

The difference between env-specific files and profiles is only logical. Microconfig handles it the same way.  

# Expression language
Microconfig supports a powerful expression language based on [Spring EL](https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#expressions).    

Let's see some examples:

```*.properties
#Better than 300000
connection.timeoutInMs=#{5 * 60 * 1000}

#Microconfig placeholder and simple math
datasource.maximum-pool-size=#{${this@datasource.minimum-pool-size} + 10} 

#Using placeholder and Java String API
healthcheck.logSucessMarker=Started ${this@mainClassNameWithoutPackage}
#var mainClassNameWithoutPackage=#{'${this@java.main}'.substring('${this@java.main}'.lastIndexOf('.') + 1)}

#Using Java import and Base64 API
sessionKey=#{T(java.util.Base64).getEncoder().encodeToString('Some value'.bytes)}  
```
Inside EL you can write any Java code in one line and Microconfig placeholders. Of course, you shouldn't overuse it to keep configuration readable.

# Arbitrary template files
Microconfig allows to keep configuration files for any libraries in their specific format and resolve placeholders inside them.
For example, you want to keep logback.xml (or some other descriptor for your log library) and reuse this file with resolved placeholders for all your services. 

Let's create this file:
```
repo
└───common
|    └───logback-template 
|     	 └───logback.xml
```
**logback-template/logback.xml**
```xml
<configuration>
    <appender class="ch.qos.logback.core.FileAppender">
        <file>logs/${application.name}.log</file>
            <encoder>
                <pattern>%d [%thread] %highlight(%-5level) %cyan(%logger{15}) %msg %n</pattern>
            </encoder>
    </appender>    
</configuration>
```
So we want every service to have its own logback.xml with resolved `${application.name}`. 
Let's configure order and payment services to use this template.

**orders/application.properties**
```*.properties
    #include service-disconvery-client
    template.logback.fromFile=${logback@folder}/logback.xml # full path to logback.xml, @folder - special placeholder property
```

**payments/application.properties**
```*.properties
    #include service-disconvery-client
    template.logback.fromFile=${logback@folder}/logback.xml
```  
   
It's better to extract the common property `template.logback.fromFile` to logback-template/application.properties and than use #include.

```
repo
└───common
|    └───logback-template 
|     	 └───logback.xml
|     	 └───application.properties
```    
**logback-template/application.properties**
```*.properties   
    template.logback.fromFile=${logback@folder}/logback.xml    
```
**orders-template/application.properties**
```*.properties
    #include service-disconvery-client
    #include logback-template
```
**payments-template/application.properties**
```*.properties
    #include service-disconvery-client
    #include logback-template
```  

As you could notice the placeholder syntax inside template `${propName}`  differs from Micronfig one `${component@propName}`, it doesn't specify a component name.
You can use standard Microconfig syntax for placeholder as well. For short sytax ${propName} == ${**this**@propName}.

If Microconfig can't resolve a template placeholder, Microconfig logs warn and leaves unresolved template text.

As we remember order and payment services include `application.name` property from service-discovery-client.
During config build Microconfig will replace `${application.name}` inside logback.xml with service's property value and copy result logback.xml to the resulting folder for each service.

If you want to declare a property for a template only and don't want this property to be included into the result config file you can use `#var` keyword. 

If you want to override template destination filename you can use `template.${templateName}.toFile=${someFile}` property. For example:  
 
 **logback-template/application.properties**
 ```*.properties   
     template.logback.fromFile=${logback@folder}/logback.xml    
     template.logback.toFile=logs/logback-descriptor.xml
 ``` 
 
You can use absolute or relative path for `toFile` property. The relative path starts from the resulting service config dir (See 'Running config build' section).

So template dependency declaration syntax looks like:   
```
template.${templateName}.fromFile=${sourceTemplateFile}    
template.${templateName}.toFile=${resolvedTemplateDestinationFile}
```
`${templateName}` - is used only for mapping `fromFile` and `toFile` properties.

Let's override file that will be copied on prod env:
```
repo
└───common
|    └───logback-template 
|     	 └───logback.xml
|     	 └───logback-prod.xml
|     	 └───application.properties
|     	 └───application.prod.properties
```
**logback-template/application.prod.properties**
```*.properties   
    template.logback.fromFile=${logback@folder}/logback-prod.xml        
``` 

# Environment descriptor
As we discussed every service can have default and environment-specific configuration, also we can extract common configuration to some components. 
During build phase we want to build configs only for a subset of our components, only for real services on a concrete environment.    
Of course you can pass env name and list of service names to build configs for. But it not too convenient if you want to build configuration for a big amount of services. 

So Microconfig allows specifying list of service names on a special environment descriptor and then use only env name to build configs for all services listed on that descriptor.

Environments descriptors must be in `${configRoot}/envs` folder.
``` 
repo
└───components
|   └───***   
└───envs
    └───base.yaml
    └───dev.yaml
    └───test.yaml
    └───prod.yaml
```

Let's see env descriptor format:
 
 **envs/base.yaml**
```*.yml
orders:  
  components:  
    - order-db-patcher
    - order-service
    - order-ui

payments:
  components:
    - payment-db-patcher
    - payment-service
    - payment-ui

infra:
  components: 
    - service-discovery
    - api-gateway
    - ssl-api-gateway
    
monitoring:
  components:
    - grafana
    - prometheus    
```  

Env name = file name
```*.yml
orders: # component group name
  components:  
    - order-db-patcher # component name(folder)
    - order-service # component name
    - order-ui # component name
``` 

One env can include another one and add/remove/override component groups:

**envs/test.yaml**
```*.yml
include: # include all groups from 'base' env except 'monitoring'
  env: base
  exclude:
   - monitoring

infra:
  exclude:
    - ssl-api-gateway # excluded ssl-api-gateway component from 'infra' group  
  append:
    - local-proxy # added new componet into 'infra' group

tests_dashboard: # aded new component group 'tests_dashboard'  
  components:
    - test-statistic-collector
``` 

You can use optional param `ip` for env and component groups and then use placeholder `${componentName@ip}`.

For instance, `${order-service@ip}` will be resolved to 12.53.12.67, `${payment-ui@ip}` will be resolved to 170.53.12.80.   
```*.yml
ip: 170.53.12.80 # default ip

orders:  
  ip: 12.53.12.67 # ip overriden for the group
  components:  
    - order-db-patcher
    - order-service
    - order-ui

payments:  
  components:
    - payment-db-patcher
    - payment-service
    - payment-ui    
```

Consider configuring your deployment tool to read env descriptor to know which services to deploy.

# Running config build
As we discussed Micronfig has its own format for configuration sources. 
During config build Micronfig inlines all includes, resolves placeholders, evaluates expression language, copies templates, and stores the result values into plain *.properties or *.yaml files to a dedicated folder for each service.

To run build you can download Microconfig release from https://github.com/microconfig/microconfig/releases.

Required build params:
* `root` - full or relative config root dir. 
* `dest` - full or relative build destination dir.
* `env` - environment name (Environment is used as a config profile, also as a group of services to build configs for).

To build configs not for the whole environment but only for specific services you can use following optional params: 
* `groups` - comma-separated list of component groups to build configs for. 
* `services` - comma-separated list of services to build configs for. 

Command line params example:
```
java -jar microconfig.jar root=repo dest=configs env=prod
```

To add system properties use `-D`
```
java -DtaskId=3456 -DsomeParam=value -jar microconfig.jar root=repo dest=configs env=prod
```

To speedup build up to 2 times you can add `-Xverify:none` and `-XX:TieredStopAtLevel=1` Java VM params. Although build time for even big projects with hungdreds of services is about 1-3 seconds.
```
java -Xverify:none -XX:TieredStopAtLevel=1 -jar microconfig.jar root=repo dest=configs env=prod
```

Let's see examples of initial and destination folder layouts: 

Initial source layout:
```
repo
└───common
|    └───logback-template 
|     	 └───logback.xml
└───core  
│    └───orders
│    │   └───application.properties
│    │   └───process.proc
│    └───payments
│        └───application.properties
│        └───process.proc
│	
└───infra
    └───service-discovery
    │   └───application.properties
    │   └───process.proc
    └───api-gateway
        └───application.properties
        └───process.proc
```
After build:
```
configs
└───orders
│   └───application.properties
│   └───process.properties
|   └───logback.xml
└───payments
│   └───application.properties
│   └───process.properties
|   └───logback.xml
└───service-discovery
│   └───application.properties
│   └───process.properties
|   └───logback.xml
└───api-gateway
    └───application.properties
    └───process.properties
    └───logback.xml
```

You can try to build configs from the dedicated example repo: https://github.com/microconfig/configs-layout-example 

# Viewing differences between config's versions 
During config build, Micronfig compares newly generated files to files generated during the previous build for each service for each config type.
Micronconfig can detect added/removed/changed properties. 

Diff for application.properties is stored in diff-application.properties, diff for process.properties is stored in diff-process.properties, etc.
```
configs
└───orders
│   └───application.properties
│   └───diff-application.properties
│   └───process.properties
│   └───diff-process.properties
|   └───logback.xml
```

Diff file format:

**diff-application.properties**
```*.properties     
+security.client.protocol=SSL # property has been added
-connection.timeoutMs=1000 # property has been removed
 server.max-threads=10 -> 35 # value has been changed from '10' to '35'
```

# YAML format support
Microconfig supports YAML format for source and result configs.
You can keep part of configuration in *.yaml files and another part in *.properties.

```
repo
└───core  
│    └───orders
│    │   └───application.yaml
│    │   └───process.proc
│    └───payments
│        └───application.properties
│        └───process.proc
```

Yaml configs can have nested properties:
```*.yml
datasource:  
  minimum-pool-size: 2  
  maximum-pool-size: 5    
  timeout:
    ms: 10
```      

and lists:
```*.yml
cluster.gateway:
  hosts:
    - 10.20.30.47
    - 15.20.30.47
    
```

Yaml format configs will be built into *.yaml, property ones will be built into *.properties. If *.properties configs include *.yaml configs, result file will be *.yaml.

Microconfig can detect config's format based on separators (if config file has extension neither *.yaml nor *.properties). If you use `:` key-value separator, Microconfig will handle it like *.yaml (`=` for *.properties).