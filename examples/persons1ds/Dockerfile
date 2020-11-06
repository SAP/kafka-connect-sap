FROM strimzi/kafka:0.19.0-kafka-2.4.1
USER root:root

RUN mkdir -p /opt/kafka/plugins/kafka-connector-hana
COPY ./target/ /opt/kafka/plugins/kafka-connector-hana/

USER 1001
