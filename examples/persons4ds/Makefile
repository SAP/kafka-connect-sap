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
	docker build . -t strimzi-connector-hana-rega

.PHONY: docker_push
docker_push:
	@echo "Pushing docker image ..."
	docker tag strimzi-connector-hana-rega ${DOCKER_REGISTRY}/strimzi-connector-hana-rega:${DOCKER_TAG}
	docker push ${DOCKER_REGISTRY}/strimzi-connector-hana-rega:${DOCKER_TAG}
