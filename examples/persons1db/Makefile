#
#
# make get_libs     - build the go binary
# make docker_build - build the docker image
#

DOCKER_TAG ?= latest

.PHONY: get_libs
get_libs:
	@echo "Getting jar files into target ..."
	@mvn install

.PHONY: docker_build
docker_build:
	@echo "Building docker image ..."
	docker build . -t debezium-connector-hana-min
