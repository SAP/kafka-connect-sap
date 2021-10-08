FROM quay.io/strimzi/kafka:0.24.0-kafka-2.8.0

USER root:root

RUN mkdir -p /opt/kafka/plugins/kafka-connector-hana
COPY ./target/ /opt/kafka/plugins/kafka-connector-hana/

USER 1001
