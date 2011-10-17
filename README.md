Debian Maven Plugin
===================

The name is self explanatory. It allows maven to prepare a Debian
package. It is not, by any means, intended to be a full on "Debian style"
support tool or anything like that. But if you want to build a basic
package from your Maven built artifacts, it will at least provide a
good start. It also includes some support for semi automatic dependency
tracking.

Usage:

Add the plugin to your POM.xml file (see <plugin> section in the following
example). Then, invoke

mvn debian:package

You probably want to start with the default values and start working from
there.  See the following example POM.xml

<groupId>com.acme</groupId>
<artifactId>roadrunner-trap</artifactId>
<version>2.5</version>

...

<dependencies>
  <dependency>
    <groupId>com.acme</groupId>
      <artifactId>nuclear-fusion</artifactId>
      <version>0.1-beta2</version>
    </dependency>
    <dependency>
      <groupId>org.hibernate</groupId>
      <artifactId>hibernate</artifactId>
      <version>3.2.5</version>
    </dependency>
</dependencies>

<build>
  <plugins> 
    <plugin>
      <groupId>com.swellimagination</groupId>
      <artifactId>debian-maven-plugin</artifactId>
      <version>1.0-SNAPSHOT</version>
        <configuration>
          <options>
    (1)     <version>2.5.99</version>
    (2)     <maintainer>${env.DEBFULLNAME} &lt;${env.DEBEMAIL}&gt;</maintainer>
            <homepage>http://acme.com</homepage>
            <description>Ultimate Roadrunner trap contortion</description>
    (3)     <installDir>/usr/local/traps</installDir>
    (4)     <targetDir>build</targetDir>
    (5)     <files>*.jar ../config/setup.ini </files>
    (6)     <architecture>mips</architecture>
    (7)     ...
          </options>
          <dependencyOverrides>
            <dependencyOverride>
    (8)       <name>org.hibernate.hibernate.*</name>
              <value>hibernate_1.99-1.1</value>
            </dependencyOverride>
          </dependencyOverrides>
        </configuration>
      </plugin>
  </plugins>
</build>

At its most basic, if you just included the plugin with NO configuration,
it would build a debian package "roadrunner-trap" with version 2.5. The
debian package would be placed under the usual standard target directory
and be named "roadrunner-trap-2.5.deb" It would depend on two packages,
nuclear-fusion (version 0.1-beta2) and hibernate(3.2.5)

The configuration is 100% optional, but you probably need at least a
couple of these elements to create your package. 

1. <version> specifies the version for the Debian package being created 
   and it is automatically taken from the version for the artifact 
   being built. In this example, we will be overriding version the version
   number 2.5 with the debian package version 2.5.99. 

2. <maintainer> specifies the name and email of the package maintainer.
   You can hardcode it here directly or, like I show in this example, use
   environment variables for example. Note that the Debian scripts are
   particularly anal about the format for this field (ok, they are pretty
   anal about everything) and it should read something along the lines of
   Wile E. Coyote <wile@acme.com> 
   (note the space between the name and the < character. Yes. It matters!)
   Also, remember to escape the angle brackets with the proper xml escape
   codes .. ie.. &lt; or &gt;)

3. The target location where the packet contents will be put on the target
   system. Defaults to /usr/share/java

4. The build directory. Defaults to Maven default "target"
5. Specifies which files, relative to the build directory, will be placed
   inside the package. Note that the files have to be present upon 
   invocation of the plugin. So unless you add the debian:package goal to
   a default build cycle phase (i.e. package), you need to make sure that 
   the product is built *before* you try to package it.. for example:
   
   maven clean package debian:package

6. Platform being targeted. Defaults to "all"    
7. For more options consult the Debial control documentation, 
   are supported but pretty much just copied verbatim from what you
   specify in the POM
8. Dependency overrides section. A one to one automatic mapping from
   a Maven dependency to a Debian package is not always meaningful, 
   possible or applicable. Here you can specify dependencies in a manual
   way. For example, here, we are overriding the dependency for
   hibernate to a given package version. Note the wildcard character
   in the dependency name. The names are evaluated so:
   <buildId>.<artifactId>_<version>
   wildcards are allowed to match a given dependency. In this manner for
   example, you could specify one big fat debian package meant to contain
   all of your other dependencies from ACME corporation.. i.e.

   <dependencyOverride>
     <name>com.acme.*</name>
     <value>acme-tools_0.1</value>
   </dependencyOverride>

   Note the use of the _ to separate the package name and version. That is
   important. 

   Also, a match group in the name can be used to capture a given version 
   number. For example, you could write the example above as:

   <dependencyOverride>
     <name>com.acme_(.*)</name>
     <value>acme-tools</value>
   </dependencyOverride>

   This would extract the version number used for this artifact and use that
   as the version number for the acme-tools package

