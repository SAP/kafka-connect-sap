#
#
# make get_libs     - build the go binary
#

DOCKER_TAG ?= latest

.PHONY: get_libs
get_libs:
	@echo "Getting jar files into target ..."
	@mkdir -p target
	@cp ../../target/kafka-connect-hana-*.jar target
	@cat driver-jars.txt | xargs  -I '{}' mvn -q dependency:get -Dartifact='{}' -Dtransitive=false -Ddest=target
