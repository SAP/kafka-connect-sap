#
#
# make get_libs     - build the go binary
# make docker_build - build the docker image
#

DOCKER_TAG ?= latest
REPO_URL = https://repo1.maven.org/maven2

.PHONY: get_libs
get_libs:
	@echo "Getting jar files into target ..."
	@mkdir -p target
	@while read -r line; do wget $(REPO_URL)/$$line -P target; done<libs.txt

.PHONY: docker_build
docker_build:
	@echo "Building docker image ..."
	docker build . -t strimzi-connector-hana-min

.PHONY: docker_tag
docker_tag:
	@echo "Tagging docker image ..."
	docker tag strimzi-connector-hana-min ${DOCKER_REGISTRY}/strimzi-connector-hana-min:${DOCKER_TAG}

.PHONY: docker_push
docker_push:
	@echo "Pushing docker image ..."
	docker push ${DOCKER_REGISTRY}/strimzi-connector-hana-min:${DOCKER_TAG}
