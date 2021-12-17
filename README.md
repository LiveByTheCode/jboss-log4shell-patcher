# JBoss application Log4Shell patcher

## Backup EVERYTHING before attempting to use this tool, because it may well corrupt everything in your domain


This hastily written tool may read the deployments section from JBoss / Wildfly's domain.xml and subsequently may remove the JndiLookup class from any log4j-core JARs in any of the deployed EARs. It could be a REAL time saver, or it could really mess stuff up.

To run:

```bash
git clone https://github.com/LiveByTheCode/jboss-log4shell-patcher.git
cd jboss-log4shell-patcher
mvn package
java -jar target/jboss-log4shell-patcher-1.0.0.jar /some/path/to/jboss/domain.xml /some/path/to/jboss/content
```

Once the app finishes it will print some new <deployments> xml. Replace the existing deployments xml with this new stuff.