### Minikube installation

This file contains setup instructions & links to run Minikube.

#### Choose installer for your OS and download

- https://minikube.sigs.k8s.io/docs/start/

#### Run Minikube

- A simple command to start a cluster is,

```
minikube start
```

- List while profiles are available

```
minikube profile list
```

- Based on our requirements, we can add command line arguments. A full list of commands is available with

```
minikube help
```

- An example


```
minikube start --memory=4092
...
```

or with more customization depending on your resources.

```
minikube start --memory=8192 --cpus=4 --kubernetes-version=v1.16.0 --vm-driver=vmware --disk-size=30g --extra-config=apiserver.enable-admission-plugins="LimitRanger,NamespaceExists,NamespaceLifecycle,ResourceQuota,ServiceAccount,DefaultStorageClass,MutatingAdmissionWebhook"

* minikube v1.12.1 on Microsoft Windows 10 Enterprise 10.0.18363 Build 18363
* Using the vmware driver based on user configuration
* Starting control plane node minikube in cluster minikube
* Creating vmware VM (CPUs=4, Memory=8192MB, Disk=30720MB) ...
* Preparing Kubernetes v1.16.0 on Docker 19.03.12 ...
  - apiserver.enable-admission-plugins=LimitRanger,NamespaceExists,NamespaceLifecycle,ResourceQuota,ServiceAccount,DefaultStorageClass,MutatingAdmissionWebhook
* Verifying Kubernetes components...
* Enabled addons: default-storageclass, storage-provisioner
* Done! kubectl is now configured to use "minikube"
```

#### Making Docker images available to Minikube

There are several ways to make Docker images available to minikube.

##### Option 1

One approah is to use Minikube's Docker daemon so that the images are directly made available to Minikube. To use Minikube's Docker daemon,
run the following command.

```
eval $(minikube docker-env)
```

Subsequently, when you run docker `docker build` and `docker tag`, the images will be loaded to Minikube.


##### Option 2

An alternative approach is to use `minikube image` to load the image into Minikube. In this case, assuming the image
is available `$DOCKER_REGISTRY/strimzi-connector-hana-min:$DOCKER_TAG`, run the following command.
```
minikube image load $DOCKER_REGISTRY/strimzi-connector-hana-min:$DOCKER_TAG
```

##### Option 3

Another similar approach is to use `docker save` and `docker load` to load the image into Minikube. Assuming the image
is available `$DOCKER_REGISTRY/strimzi-connector-hana-min:$DOCKER_TAG`, run the following command.
```
docker save $DOCKER_REGISTRY/strimzi-connector-hana-min:latest | (eval $(minikube docker-env) && docker load)
```

##### Verify if the images are available

To verify whether the images are available in Minikube, run the following command to list images.

```
minikube ssh docker images
```

#### Initialize helm

- To begin working with Helm, Tiller needs to be installed in the running Kubernetes cluster. The below command will setup helm.

```
helm init
```
