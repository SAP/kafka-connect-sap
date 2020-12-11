### Minikube installation

This file contains setup instructions & links to run Minikube.

#### Choose installer for our OS and download

- https://minikube.sigs.k8s.io/docs/start/

#### Run minikube

- A simple command to start a cluster is,

```
minikube start
```

- Based on our requirements, we can add command line arguments. A full list of commands is available with

```
minikube help
```

- An example 
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

#### Initialize helm

- To begin working with Helm, Tiller needs to be installed in the running Kubernetes cluster. The below command will setup helm.

```
helm init
```