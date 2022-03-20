#
#
# make get_libs     - download dependent libraries
#

DOCKER_TAG ?= latest
REPO_URL = https://repo1.maven.org/maven2
CONFLUENT_REPO_URL = https://packages.confluent.io/maven

.PHONY: get_libs
get_libs:
	@echo "Getting jar files into target ..."
	@mkdir -p target
	@grep -v confluent libs.txt | while read -r line; do wget $(REPO_URL)/$$line -P target; done
	@grep confluent libs.txt | while read -r line; do wget $(CONFLUENT_REPO_URL)/$$line -P target; done

