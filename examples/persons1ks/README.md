### Example persons1ks: kafka-hana-connect using strimzi kafka images in kubernetes

This example is a kubernetes version of example persons1ds.

#### Prerequisites

- example persons1ds is built
- Access to HANA
- Kubernetes

#### Running

This description assumes Docker and Kubernetes CLI (`kubectl`) are available on local machine and a Kubernetes cluster is available.

##### Step 1: Prepare Docker image for kafka-connector-hana

We use the Docker image built in example persons1ds. To make this image available to the Kubernetes cluster, push the image to the Docker regisry.

Make sure that `DOCKER_REGISTRY` is set to the registry used (e.g., `kubernetes.docker.internal:5000` when using a local registry with Docker Desktop) 

```
$ cd ../persons1ds
$ echo $DOCKER_REGISTRY
kubernetes.docker.internal:5000
$ 
$ make docker_push
Pushing docker image ...
docker tag strimzi-connector-hana-min kubernetes.docker.internal:5000/strimzi-connector-hana-min:latest
docker push kubernetes.docker.internal:5000/strimzi-connector-hana-min:latest
The push refers to repository [kubernetes.docker.internal:5000/strimzi-connector-hana-min]
62e63530617f: Layer already exists
...
latest: digest: sha256:62a0eef8b35fb8cdcb80e807ade2dc774bc16076351ac7124ef873545c0ba001 size: 4918
$
$ cd ../persons1ks
```

##### Step 2: Prepare Kubernetes cluster for kafka-connector-hana

We create a namespace `kafka` for this installation. You are free to use another namespace.

```
$ kubectl create ns kafka
namespace/kafka created
$
```

We install `strimzi` Kafka operator using its helm chart.

Add `strimzi` helm chart repo to your helm repositories if it is not added yet.

```
$ helm repo add strimzi https://strimzi.io/charts/
"strimzi" has been added to your repositories
$
```

NOTE: `helm install` has different syntax depending on its version v2 or v3. The release name is expected by v3 whereas it is not expected by v2. When using v2, omit the first argument below.

```
$ heml version
version.BuildInfo{Version:"v3.1.2", GitCommit:"d878d4d45863e42fd5cff6743294a11d28a9abce", GitTreeState:"clean", GoVersion:"go1.13.8"}
$ helm install my-strimzi-release strimzi/strimzi-kafka-operator -n kafka --version 0.19.0
NAME: my-strimzi-release
LAST DEPLOYED: Tue Aug 25 17:28:30 2020
NAMESPACE: kafka
STATUS: deployed
REVISION: 1
TEST SUITE: None
NOTES:
Thank you for installing strimzi-kafka-operator-0.19.0

To create a Kafka cluster refer to the following documentation.

https://strimzi.io/docs/operators/0.19.0/using.html#deploying-cluster-operator-helm-chart-str
$ kubectl get pod -l strimzi.io/kind=cluster-operator -n $kafka
NAME                                        READY   STATUS    RESTARTS   AGE
strimzi-cluster-operator-55dd5ccd6f-s5rw6   1/1     Running   0          3m23s
$ 
```

##### Step 3: Install Kafka, Zookeeper for kafka-connector-hana

Install Kafka and Zookeeper by applying file `kafka-ephemeral-single.yaml` and verify the status.

```
$ kubectl apply -f kafka-ephemeral-single.yaml -n kafka
kafka.kafka.strimzi.io/my-cluster created
$
$ kubectl get po -n kafka
NAME                                          READY   STATUS    RESTARTS   AGE
my-cluster-entity-operator-7f8cd6f7fc-bdjz6   3/3     Running   0          2m6s
my-cluster-kafka-0                            2/2     Running   0          2m34s
my-cluster-zookeeper-0                        1/1     Running   0          2m55s
strimzi-cluster-operator-68b6d59f74-vrx47     1/1     Running   0          23h
$
$ kubectl get svc -n kafka
NAME                             TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)                      AGE
my-cluster-kafka-bootstrap       ClusterIP   10.111.65.81    <none>        9091/TCP,9092/TCP,9093/TCP   20m
my-cluster-kafka-brokers         ClusterIP   None            <none>        9091/TCP,9092/TCP,9093/TCP   20m
my-cluster-zookeeper-client      ClusterIP   10.105.207.47   <none>        2181/TCP                     21m
my-cluster-zookeeper-nodes       ClusterIP   None            <none>        2181/TCP,2888/TCP,3888/TCP   21m
$
```

##### Step 4: Install Kafka-Connect for kafka-connector-hana

Install Kafka connect with the connector by applying file `kafka-connect-hana-min.yaml`.
Make sure to adjust the image property value to match the name of the image created in Step 1.

```
$ kubectl apply -f kafka-connect-hana-min.yaml -n kafka
kafkaconnect.kafka.strimzi.io/my-connect-cluster created
$
$ kubectl get po -n kafka 
NAME                                          READY   STATUS    RESTARTS   AGE
my-cluster-entity-operator-7f8cd6f7fc-bdjz6   3/3     Running   0          20m
my-cluster-kafka-0                            2/2     Running   0          21m
my-cluster-zookeeper-0                        1/1     Running   0          21m
my-connect-cluster-connect-7bdbdbff64-5k4ms   1/1     Running   0          100s
strimzi-cluster-operator-68b6d59f74-vrx47     1/1     Running   0          23h
$
$ kubectl get svc -n kafka
NAME                             TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)                      AGE
my-cluster-kafka-bootstrap       ClusterIP   10.111.65.81    <none>        9091/TCP,9092/TCP,9093/TCP   21m
my-cluster-kafka-brokers         ClusterIP   None            <none>        9091/TCP,9092/TCP,9093/TCP   21m
my-cluster-zookeeper-client      ClusterIP   10.105.207.47   <none>        2181/TCP                     22m
my-cluster-zookeeper-nodes       ClusterIP   None            <none>        2181/TCP,2888/TCP,3888/TCP   22m
my-connect-cluster-connect-api   ClusterIP   10.104.58.181   <none>        8083/TCP                     116s
$
```


##### Step 5: Prepare the connector configuration files

Follow the step for persons1ds to prepare the connector json files.

As the above configuration does not expose the external port from Kafka Connect's pod `my-connect-cluster-connect-api`, open another console and add port-forwarding to the local system.

```
$ kubectl port-forward my-connect-cluster-connect-7bdbdbff64-5k4ms 8083:8083 -n kafka
Forwarding from 127.0.0.1:8083 -> 8083
Forwarding from [::1]:8083 -> 8083
...
```

You can verify whether Kafka Connect is running using curl.

```
$ curl -i http://localhost:8083/
HTTP/1.1 200 OK
Date: Thu, 26 Nov 2020 16:34:18 GMT
Content-Type: application/json
Content-Length: 91
Server: Jetty(9.4.20.v20190813)

{"version":"2.4.1","commit":"c57222ae8cd7866b","kafka_cluster_id":"TUbAajkpT5q9nUckIM62Dg"}
$
```

Follow the step in persons1ds to install `connect-hana-source-1.json` and `connect-hana-sink-1.json`.

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

Use `kafka-topics.sh` to see topic `test_topic_1` is present.

```
[kafka@my-cluster-kafka-0 kafka]$ bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
__consumer_offsets
connect-cluster-configs
connect-cluster-offsets
connect-cluster-status
test_topic_1
[kafka@my-cluster-kafka-0 kafka]$
```

Use `kafka-console-consumer.sh` to fetch some messages.

```
[kafka@my-cluster-kafka-0 kafka]$ bin/kafka-console-consumer.sh --bootstrap-server  localhost:9092 --topic test_topic_1 --from-beginning
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":1,"LASTNAME":"simpson","FIRSTNAME":"homer"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":2,"LASTNAME":"simpson","FIRSTNAME":"merge"}}
{"schema":{"type":"struct","fields":[{"type":"int32","optional":false,"field":"PERSONID"},{"type":"string","optional":true,"field":"LASTNAME"},{"type":"string","optional":true,"field":"FIRSTNAME"}],"optional":false,"name":"d025803persons1"},"payload":{"PERSONID":3,"LASTNAME":"simpson","FIRSTNAME":"bart"}}
...
```

##### Step 7: Shut down

Use `kubectl delete` to uninstall the resources.


```
$ kubectl delete -f kafka-connect-hana-min.yaml -n kafka
kafkaconnect.kafka.strimzi.io "my-connect-cluster" deleted
$ kubectl delete -f kafka-ephemeral-single.yaml -n kafka 
kafka.kafka.strimzi.io "my-cluster" deleted
$ kubectl get svc -n kafka
No resources found in kafka namespace.
$ kubectl get po -n kafka
NAME                                        READY   STATUS    RESTARTS   AGE
strimzi-cluster-operator-68b6d59f74-vrx47   1/1     Running   0          23h
$ 
```
