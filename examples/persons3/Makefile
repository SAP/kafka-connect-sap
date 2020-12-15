#
#
# make get_libs     - build the go binary
#

DOCKER_TAG ?= latest
REPO_URL = https://repo1.maven.org/maven2

.PHONY: get_libs
get_libs:
	@echo "Getting jar files into target ..."
	@mkdir -p target
	@while read -r line; do wget $(REPO_URL)/$$line -P target; done<libs.txt

