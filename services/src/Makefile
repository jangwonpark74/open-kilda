#
KILDA_MAVEN := ~/.m2/repository/org/bitbucket/openkilda
PARENT_JAR := $(KILDA_MAVEN)/kilda-parent/1.0-SNAPSHOT/kilda-parent-1.0-SNAPSHOT.pom
MESSAGING_JAR :=  $(KILDA_MAVEN)/messaging/1.0-SNAPSHOT/messaging-1.0-SNAPSHOT.jar
FLOODLIGHT_JAR := ~/.m2/repository/org/projectfloodlight/floodlight/1.2-SNAPSHOT/floodlight-1.2-SNAPSHOT.jar
FM_JAR := floodlight-modules/target/floodlight-modules.jar

default: speaker messaging northbound floodlight-modules atdd

speaker:
	$(MAKE) -C projectfloodlight

$(MESSAGING_JAR):
	mvn -f messaging/pom.xml clean install

$(PARENT_JAR):
	mvn --non-recursive clean install

messaging: $(PARENT_JAR) $(MESSAGING_JAR)

northbound: $(MESSAGING_JAR)
	mvn -f northbound/pom.xml clean package

floodlight-modules: $(MESSAGING_JAR)
	$(MAKE) -C projectfloodlight
	mvn -f floodlight-modules/pom.xml clean package

atdd: $(MESSAGING_JAR)
	mvn -f atdd/pom.xml clean package

run-speaker: speaker floodlight-modules
	java -Dlogback.configurationFile=services/src/floodlight-modules/src/test/resources/logback.xml \
	-cp $(FLOODLIGHT_JAR):$(FM_JAR) net.floodlightcontroller.core.Main \
	-cf floodlight-modules/src/main/resources/floodlightkilda.properties

clean:
	mvn -f floodlight-modules/pom.xml clean
	mvn -f northbound/pom.xml clean
	mvn -f messaging/pom.xml clean
	$(MAKE) -C projectfloodlight clean
	rm -rf $(KILDA_MAVEN)

.PHONY: default speaker messaging northbound floodlight-modules atdd run-speaker clean