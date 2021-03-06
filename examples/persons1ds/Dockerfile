FROM strimzi/kafka:0.20.1-kafka-2.6.0

USER root:root

RUN mkdir -p /opt/kafka/plugins/kafka-connector-hana
COPY ./target/ /opt/kafka/plugins/kafka-connector-hana/

USER 1001
