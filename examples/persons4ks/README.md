### Example persons4ks: kafka-hana-connect using strimzi kafka images in kubernetes

This example is a kubernetes version of example persons4ds.

#### Prerequisites

- example persons4ds is built
- Access to HANA
- Kubernetes

#### Running

This description assumes Docker and Kubernetes CLI (`kubectl`) are available on local machine and a Kubernetes cluster is available.

##### Step 1: Prepare Docker image for kafka-connector-hana

We use the Docker image built in example persons1ds. To make this image available to the Kubernetes cluster, push the image to the Docker regisry.

Make sure that `DOCKER_REGISTRY` is set to the registry used (e.g., `kubernetes.docker.internal:5000` when using a local registry with Docker Desktop) 



```
$ cd ../persons4ds
$ echo $DOCKER_REGISTRY
kubernetes.docker.internal:5000
$ make docker_push     
Pushing docker image ...
docker tag strimzi-connector-hana-rega kubernetes.docker.internal:5000/strimzi-connector-hana-rega:latest
docker push kubernetes.docker.internal:5000/strimzi-connector-hana-rega:latest
The push refers to repository [kubernetes.docker.internal:5000/strimzi-connector-hana-rega]
56de5af4fe79: Layer already exists
...
latest: digest: sha256:48930655ad18431f7405f5338e8e1656f5be8cc20f888b3f22b327891e091808 size: 4918
$
$ cd ../persons4ks
```

##### Steps 2, 3: Follow steps 2 and 3 of persons1ks

##### Step 4: Install Apicurio Schema-Registry and Kafka-Connect for kafka-connector-hana

Install Apicurio Schema-Registry by applying file `apicurio-registry.yaml`.

```
$ kubectl apply -f apicurio-registry.yaml -n kafka
deployment.apps/my-cluster-schema-registry created
service/my-cluster-schema-registry-api created
$
```

Install Kafka connect with the connector by applying file `kafka-connect-hana-raga.yaml`.

```
$ kubectl apply -f kafka-connect-hana-rega.yaml
kafkaconnect.kafka.strimzi.io/my-connect-cluster created
$
$ kubectl get po -n kafka
NAME                                          READY   STATUS    RESTARTS   AGE
my-cluster-entity-operator-7f8cd6f7fc-sfr8x   3/3     Running   0          3m47s
my-cluster-kafka-0                            2/2     Running   0          4m12s
my-cluster-schema-registry-6b864c5b4-7jjfq    1/1     Running   0          4m29s
my-cluster-zookeeper-0                        1/1     Running   0          4m42s
my-connect-cluster-connect-5d9b68fdb5-7rt2b   1/1     Running   0          89s
strimzi-cluster-operator-68b6d59f74-vrx47     1/1     Running   1          3d22h
$
$ kubectl get svc -n kafka
NAME                             TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)                      AGE
my-cluster-kafka-bootstrap       ClusterIP   10.103.134.138   <none>        9091/TCP,9092/TCP,9093/TCP   4m21s
my-cluster-kafka-brokers         ClusterIP   None             <none>        9091/TCP,9092/TCP,9093/TCP   4m21s
my-cluster-schema-registry-api   ClusterIP   10.108.5.129     <none>        8080/TCP                     4m38s
my-cluster-zookeeper-client      ClusterIP   10.100.146.224   <none>        2181/TCP                     4m51s
my-cluster-zookeeper-nodes       ClusterIP   None             <none>        2181/TCP,2888/TCP,3888/TCP   4m51s
my-connect-cluster-connect-api   ClusterIP   10.103.61.217    <none>        8083/TCP                     98s
$
```

##### Step 5: Prepare the connector configuration files

Follow the step for persons4ds to prepare the connector json files and make sure the following converter properties are set.

```
{
    "name": "test-topic-4-source",
    "config": {
    ...
        "value.converter": "io.apicurio.registry.utils.converter.AvroConverter",
        "value.converter.apicurio.registry.url": "http://my-cluster-schema-registry-api:8080/api",
        "value.converter.apicurio.registry.converter.serializer": "io.apicurio.registry.utils.serde.AvroKafkaSerializer",
        "value.converter.apicurio.registry.converter.deserializer": "io.apicurio.registry.utils.serde.AvroKafkaDeserializer",
        "value.converter.apicurio.registry.global-id": "io.apicurio.registry.utils.serde.strategy.GetOrCreateIdStrategy"
    }
}
```

As the above configuration does not expose the external port from Kafka Connect's pod `my-connect-cluster-connect-api`, open another console and add port-forwarding to the local system.

```

##### Step 6: Verifying the result (Follow Step 6 of example persions1 and/or persons2)

You can connect to the Kafka broker pod to directly inspect the topic or verify the target HANA table.

```
$ kubectl exec -it my-cluster-kafka-0 -n kafka -- bash
Defaulting container name to kafka.
Use 'kubectl describe pod/my-cluster-kafka-0 -n kafka' to see all of the containers in this pod.
[kafka@my-cluster-kafka-0 kafka]$ ls
LICENSE                            kafka_connect_run.sh                                kafka_run.sh
NOTICE                             kafka_connect_tls_prepare_certificates.sh           kafka_tls_prepare_certificates.sh
bin                                kafka_liveness.sh                                   libs
broker-certs                       kafka_mirror_maker_2_connector_config_generator.sh  s2i
client-ca-certs                    kafka_mirror_maker_2_run.sh                         set_kafka_gc_options.sh
cluster-ca-certs                   kafka_mirror_maker_consumer_config_generator.sh     site-docs
config                             kafka_mirror_maker_liveness.sh                      to_bytes.gawk
custom-config                      kafka_mirror_maker_producer_config_generator.sh     zookeeper_config_generator.sh
dynamic_resources.sh               kafka_mirror_maker_run.sh                           zookeeper_healthcheck.sh
kafka_config_generator.sh          kafka_mirror_maker_tls_prepare_certificates.sh      zookeeper_run.sh
kafka_connect_config_generator.sh  kafka_pre_start.sh                                  zookeeper_tls_prepare_certificates.sh
[kafka@my-cluster-kafka-0 kafka]$
```

Use `kafka-topics.sh` to see topic `test_topic_4` is present.

```
[kafka@my-cluster-kafka-0 kafka]$ bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
__consumer_offsets
connect-cluster-configs
connect-cluster-offsets
connect-cluster-status
test_topic_1
test_topic_4
[kafka@my-cluster-kafka-0 kafka]$
```

Use `kafka-console-consumer.sh` to fetch some messages.

```
[kafka@my-cluster-kafka-0 kafka]$ bin/kafka-console-consumer.sh --bootstrap-server  localhost:9092 --topic test_topic_4 --from-beginning
simpson
homer
simpson
marge
simpsobart
simpsolisa

simpson
       maggie
...
```

You can verify that the schema is stored in the registry

```
[kafka@my-cluster-kafka-0 kafka]$ curl -i http://my-cluster-schema-registry-api:8080/api/artifacts
HTTP/1.1 200 OK
Date: Fri, 27 Nov 2020 15:49:32 GMT
Expires: Thu, 26 Nov 2020 15:49:32 GMT
Pragma: no-cache
Cache-control: no-cache, no-store, must-revalidate
Content-Type: application/json
Content-Length: 22

["test_topic_4-value"]
[kafka@my-cluster-kafka-0 kafka]$
[kafka@my-cluster-kafka-0 kafka]$ curl -i http://my-cluster-schema-registry-api:8080/api/artifacts/test_topic_4-value
HTTP/1.1 200 OK
Date: Fri, 27 Nov 2020 15:49:52 GMT
Expires: Thu, 26 Nov 2020 15:49:52 GMT
Pragma: no-cache
Cache-control: no-cache, no-store, must-revalidate
Content-Type: application/json
Content-Length: 240

{"type":"record","name":"d025803persons4","fields":[{"name":"PERSONID","type":"int"},{"name":"LASTNAME","type":["null","string"],"default":null},{"name":"FIRSTNAME","type":["null","string"],"default":null}],"connect.name":"d025803persons4"}
[kafka@my-cluster-kafka-0 kafka]$
```

##### Step 7: Shut down

Use `kubectl delete` to uninstall the resources.

```
$ kubectl delete -f kafka-connect-hana-rega.yaml -n kafka
kafkaconnect.kafka.strimzi.io "my-connect-cluster" deleted
$ kubectl delete -f apicurio-registry.yaml -n kafka
deployment.apps "my-cluster-schema-registry" deleted
service "my-cluster-schema-registry-api" deleted
$ kubectl delete -f kafka-ephemeral-single.yaml -n kafka 
kafka.kafka.strimzi.io "my-cluster" deleted
$
$ kubectl get po -n kafka                                                   
NAME                                        READY   STATUS        RESTARTS   AGE
strimzi-cluster-operator-68b6d59f74-vrx47   1/1     Running       1          3d22h
$
```
