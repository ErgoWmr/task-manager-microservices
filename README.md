# Projet Task Manager — Microservices / Docker / Kubernetes

Projet du cours Docker/Kubernetes (Benoît Charroux, M2). Application de gestion de tâches type todo/kanban, déclinée en microservices Spring Boot, déployés sur Kubernetes derrière une gateway, avec une base PostgreSQL partagée.

**Auteurs (binôme)** : Younes OUAMAR & Max PENSO
**Repo / images** : `ErgoWmr` (GitHub) / `ergowmr` (Docker Hub)

## Roadmap (notation progressive du sujet)

| Palier | Étape                                                      | Statut |
|--------|------------------------------------------------------------|--------|
| 10/20  | 1 service local + Dockerfile + Docker Hub + K8s deploy/svc | ✅ |
| 12/20  | Gateway (Ingress NGINX → migrée vers Istio au palier 18)   | ✅ |
| 14/20  | 2e service relié au 1er via DNS K8s                        | ✅ |
| 16/20  | Base de données partagée (PostgreSQL sur K8s + PVC)        | ✅ |
| 18/20  | Sécurité : Istio mTLS STRICT + RBAC K8s + AuthorizationPolicy + scan Docker Hub | ✅ |
| 20/20  | Cloud (option bonus)                                       | — |

## Architecture

Tous les pods app embarquent un **sidecar Envoy** (Istio) — toute communication inter-pod passe par lui en **mTLS STRICT**. Chaque service tourne sous un **ServiceAccount dédié** (RBAC K8s) et n'accepte les requêtes entrantes que des sources explicitement autorisées par une **AuthorizationPolicy** Istio (zero-trust).

```
                            ┌───────────────────────────┐
            navigateur ───▶ │  Istio Ingress Gateway    │  taskmanager.local
                            │  (port-forward → 8080)    │
                            └──┬───────┬─────────────┬──┘
                               │       │             │
                          /    │       │ /api/users  │  /api/tasks
                               │       │             │
                       ┌───────▼─┐    ┌▼────────────▼─────┐
                       │ frontend│    │ users-svc          │
                       │ React + │    │ Spring Boot 2x     │◀─┐
                       │ nginx 2x│    │ + sidecar Envoy    │  │ mTLS via sidecars
                       │ +sidecar│    └─────────┬──────────┘  │ (DNS K8s)
                       └─────────┘              │             │
                                                ▼             │
                                       ┌─────────────────┐    │
                                       │ tasks-svc       ├────┘
                                       │ Spring Boot 2x  │
                                       │ + sidecar Envoy │
                                       └────────┬────────┘
                                                │ JDBC (mTLS L4 via sidecars)
                                       ┌────────▼────────────┐
                                       │ PostgreSQL 16       │
                                       │ 1 pod + sidecar     │ taskmanagerdb
                                       │ PVC 5Gi RWO         │ envFrom Secret+ConfigMap
                                       └────────┬────────────┘
                                                │
                                       ┌────────▼────────────┐
                                       │ PV hostPath /mnt/data│
                                       └─────────────────────┘
```

- **frontend** (`ergowmr/task-manager-front:1`) : SPA Vite/React, servie par nginx. Consomme `/api/users` et `/api/tasks` en relatif.
- **users-service** (`ergowmr/users-service:2`) : CRUD users (Spring Boot 3.2 / Java 21 / JPA / driver Postgres).
- **tasks-service** (`ergowmr/tasks-service:3`) : CRUD tâches + endpoint `/tasks/{id}/full` qui appelle users-service via DNS K8s pour enrichir avec l'assignee.
- **postgres** (`postgres:16-alpine`) : base partagée `taskmanagerdb` (tables `users` et `tasks`).
- **gateway** : **Istio Gateway + VirtualService + DestinationRule** (style `charroux/rentalservice`), avec 3 routes (users / tasks / frontend). Le `Ingress NGINX` du palier 12 est archivé sous `k8s/legacy/`.
- **sécurité réseau** : `PeerAuthentication` STRICT au niveau du namespace (mtls-migration de la doc Istio).
- **sécurité K8s** : un `ServiceAccount` par workload, `Role` minimal scope au strict nécessaire, `automountServiceAccountToken: false` pour les workloads qui n'utilisent pas l'API K8s.
- **sécurité applicative** : `AuthorizationPolicy` Istio identifie l'appelant via SPIFFE ID dans le cert mTLS et n'autorise que les flux légitimes (zero-trust deny-by-default).
- **persistance** : PV hostPath 5Gi → PVC ReadWriteOnce → mount `/var/lib/postgresql/data`.

## Layout

```
projet-task-manager/
├── README.md
├── .gitignore
├── users-service/                  Spring Boot 3.2 / Gradle / Java 21
│   ├── build.gradle / settings.gradle
│   ├── Dockerfile                  multi-stage gradle:8.5-jdk21 → eclipse-temurin:21-jre
│   └── src/main/java/com/ergowmr/taskmanager/users/
│       ├── UsersServiceApplication.java
│       ├── HealthRest.java
│       ├── User.java               @Entity JPA
│       ├── UserRepository.java     extends JpaRepository
│       └── UserController.java     CRUD + seed idempotent
├── tasks-service/                  même stack
│   └── src/main/java/com/ergowmr/taskmanager/tasks/
│       ├── TasksServiceApplication.java   + @Bean RestClient(users-service)
│       ├── HealthRest.java
│       ├── Task.java               @Entity JPA (status enum, assigneeId)
│       ├── TaskRepository.java
│       ├── UsersClient.java        appel inter-service via RestClient
│       └── TaskController.java     CRUD + /tasks/{id}/full (enrichissement)
├── k8s/
│   ├── users-service-deployment.yml  + Postgres env vars (Secret/ConfigMap)
│   ├── users-service-service.yml     NodePort 8080
│   ├── tasks-service-deployment.yml  + USERS_SERVICE_URL + Postgres env vars
│   ├── tasks-service-service.yml     ClusterIP 8080
│   ├── gateway-ingress.yml           2 Ingress (users + tasks) avec rewrite spécifique
│   ├── frontend-deployment.yml       2 replicas nginx, probes /healthz, SA frontend-sa
│   ├── frontend-service.yml          ClusterIP 80, port nommé http
│   ├── istio/                        Palier 18/20 — gateway et sécurité réseau
│   │   ├── gateway.yml               Istio Gateway (sélecteur istio: ingressgateway)
│   │   ├── virtualservices.yml      3 routes (users, tasks, frontend) avec timeouts/retries
│   │   ├── destinationrules.yml     LB LEAST_REQUEST + connection pool + outlier detection
│   │   ├── peerauthentication.yml   mTLS STRICT (palier 18 ; PERMISSIVE archivé en commentaires)
│   │   └── authorization-policies.yml zero-trust : qui peut appeler qui (SPIFFE ID)
│   ├── rbac/                         Palier 18/20 — sécurité K8s
│   │   ├── service-accounts.yml     1 SA par workload (users, tasks, frontend, postgres)
│   │   └── roles.yml                Roles + RoleBindings minimaux (deny-by-default)
│   ├── legacy/                       Manifests palier 12 archivés (NGINX Ingress)
│   │   └── gateway-ingress-nginx.yml
│   └── postgres/
│       ├── postgres-secret.yml       POSTGRES_PASSWORD (Opaque Secret)
│       ├── postgres-configmap.yml    POSTGRES_DB + POSTGRES_USER
│       ├── postgres-storage.yml      PV 5Gi hostPath /mnt/data + PVC
│       ├── postgres-deployment.yml   1 replica, envFrom secret+configmap, SA postgres-sa
│       └── postgres-service.yml      NodePort 5432, port nommé tcp-postgres
├── frontend/                       SPA Vite + React
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   ├── nginx.conf                  SPA fallback + cache assets
│   ├── Dockerfile                  multi-stage node:20 → nginx:1.27
│   └── src/
│       ├── main.jsx
│       ├── App.jsx                 fetch /api/users + /api/tasks, modale /full
│       └── styles.css
└── docs/                            captures d'écran, mini rapport
```

## Déploiement from scratch

### Prérequis

- Docker Desktop / Engine (build des images, `minikube --driver=docker`)
- minikube + kubectl
- Sur Windows avec docker driver : `minikube tunnel` doit tourner pour exposer l'Ingress sur `127.0.0.1`

### 1. Build + push des images

```bash
cd users-service && docker build -t ergowmr/users-service:2 . && docker push ergowmr/users-service:2 && cd ..
cd tasks-service && docker build -t ergowmr/tasks-service:3 . && docker push ergowmr/tasks-service:3 && cd ..
cd frontend      && docker build -t ergowmr/task-manager-front:1 . && docker push ergowmr/task-manager-front:1 && cd ..
```

Images publiques :
- https://hub.docker.com/r/ergowmr/users-service
- https://hub.docker.com/r/ergowmr/tasks-service
- https://hub.docker.com/r/ergowmr/task-manager-front

### 2. Démarrer le cluster

```bash
minikube start
minikube ssh -- "sudo mkdir -p /mnt/data && sudo chmod 777 /mnt/data"   # hostPath du PV
```

### 3. Installer Istio (palier 18/20)

```bash
# Télécharger istioctl (Windows .zip à extraire, le binaire reste dans DEVOPS/istio-1.x.y/bin/)
curl -sLo istio.zip https://github.com/istio/istio/releases/latest/download/istio-1.29.2-win.zip
unzip -q istio.zip
ISTIOCTL=./istio-1.29.2/bin/istioctl.exe

# Install profile default (istiod + ingressgateway), suffisant pour 4 GB de RAM minikube
$ISTIOCTL install --set profile=default -y
kubectl label namespace default istio-injection=enabled --overwrite
```

### 4. Appliquer les manifests dans l'ordre

```bash
# 4.1) RBAC (les SA doivent exister avant les Deployments qui les référencent)
kubectl apply -f k8s/rbac/

# 4.2) Postgres
kubectl apply -f k8s/postgres/
kubectl rollout status deployment/postgres --timeout=180s

# 4.3) Services applicatifs + frontend
kubectl apply -f k8s/users-service-deployment.yml -f k8s/users-service-service.yml
kubectl apply -f k8s/tasks-service-deployment.yml -f k8s/tasks-service-service.yml
kubectl apply -f k8s/frontend-deployment.yml -f k8s/frontend-service.yml

# 4.4) Couche Istio : Gateway + VS + DR + mTLS STRICT + AuthorizationPolicy
kubectl apply -f k8s/istio/
```

### 5. Accéder à la plateforme

Sur Windows + driver docker, le port 80 nécessite admin. Plus simple : `kubectl port-forward` sur 8080 (no admin requis).

```bash
# Terminal séparé, à laisser tourner :
kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80
```

Tester les API :

```bash
curl -H "Host: taskmanager.local" http://127.0.0.1:8080/api/users
curl -H "Host: taskmanager.local" http://127.0.0.1:8080/api/tasks
curl -H "Host: taskmanager.local" http://127.0.0.1:8080/api/tasks/4/full   # appel inter-service
```

Pour ouvrir le **front React dans un navigateur**, ajouter une entrée dans le fichier hosts (admin requis) :

```
# C:\Windows\System32\drivers\etc\hosts (Windows) ou /etc/hosts (Linux/macOS)
127.0.0.1   taskmanager.local
```

Puis ouvrir **http://taskmanager.local:8080** (le port 8080 vient du port-forward).

## Endpoints

### users-service (`/api/users` via gateway)

| Méthode | Path                  | Action                              |
|---------|-----------------------|-------------------------------------|
| GET     | `/api/users`          | liste tous les users                |
| GET     | `/api/users/{id}`     | retourne un user                    |
| POST    | `/api/users`          | crée (`{username, email}`)          |
| DELETE  | `/api/users/{id}`     | supprime un user                    |
| GET     | `/api/users/`         | (root du service) info service      |
| GET     | `/api/users/status`   | hostname du pod, état               |

### tasks-service (`/api/tasks` via gateway)

| Méthode | Path                       | Action                                                            |
|---------|----------------------------|-------------------------------------------------------------------|
| GET     | `/api/tasks`               | liste toutes les tâches                                           |
| GET     | `/api/tasks/{id}`          | retourne une tâche                                                |
| GET     | `/api/tasks/{id}/full`     | tâche **enrichie** avec l'assignee (appel inter-service à users)  |
| POST    | `/api/tasks`               | crée (`{title, description, status, assigneeId}`)                 |
| DELETE  | `/api/tasks/{id}`          | supprime                                                          |

### Postgres (interne au cluster)

```bash
PG=$(kubectl get pod -l app=postgres -o jsonpath='{.items[0].metadata.name}')
kubectl exec "$PG" -- psql -U taskmanager -d taskmanagerdb -c '\dt'
```

## Notes de conception

- **DB partagée** entre les 2 services (1 base `taskmanagerdb`, 2 tables) — choix conforme aux schémas du PDF du sujet, qui montrent une seule "Service base de données".
- **Validation locale en H2 avant K8s** — comme conseillé par le prof ("Dans un premier temps réaliser le codage d'utilisation d'une base de données via une base in memory"). Le datasource est paramétrable par variables d'environnement (`SPRING_DATASOURCE_*`), donc même image en local (H2 par défaut) ou en K8s (override Postgres).
- **Image base** : `postgres:16-alpine` au lieu de `postgres:10.1` du repo `charroux/noops/postgres` — la 10.1 est EOL depuis nov 2022. Pattern de manifests strictement identique.
- **Seed idempotent** : `users-service` catch `DataIntegrityViolationException` au seed pour gérer la race entre 2 replicas qui démarrent en parallèle.
- **Inter-service** : `tasks-service` appelle `http://users-service:8080` via le DNS K8s du Service. Le RestClient est configuré au démarrage (`@Bean` dans `TasksServiceApplication`).
- **mTLS migration** (palier 18, [doc Istio](https://istio.io/latest/docs/tasks/security/authentication/mtls-migration/) citée par le PDF) : on a démarré en `PERMISSIVE` (pattern `charroux/rentalservice`), validé que le mesh chiffrait déjà, puis basculé en `STRICT`. Démontré avec un pod hors mesh (namespace `nomesh`) qui passait en PERMISSIVE et est refusé en STRICT.
- **Ports nommés sur les Services** : Istio impose une convention de nommage (`http`, `tcp-postgres`, etc.) pour identifier le protocole et router correctement le trafic via les sidecars.
- **Zero-trust** : combinaison RBAC K8s (limite l'API K8s) + AuthorizationPolicy Istio (limite le trafic mesh). Frontend ne peut pas appeler users-service en direct, tasks-service ne peut pas écrire dans Postgres si pas autorisé, etc.

## Conventions images Docker Hub

Chaque tag numérique correspond à un jalon du projet — la trace est visible dans Docker Hub.

**users-service**
| Tag | Jalon                                                                  |
|-----|------------------------------------------------------------------------|
| `1` | Palier 10/20 — REST CRUD users en mémoire (`ConcurrentHashMap`)        |
| `2` | Palier 16/20 — JPA + driver Postgres, datasource paramétrable par env  |

**tasks-service**
| Tag | Jalon                                                                                |
|-----|--------------------------------------------------------------------------------------|
| `1` | Palier 14/20 — REST CRUD tâches en mémoire + appel inter-service à users-service     |
| `2` | Palier 16/20 étape 1 — JPA + H2 in-memory (validation locale comme conseillé par le prof) |
| `3` | Palier 16/20 étape finale — JPA + driver Postgres, branché au pod `postgres` du cluster |

**task-manager-front**
| Tag | Jalon                                                  |
|-----|--------------------------------------------------------|
| `1` | Bonus présentation — SPA Vite + React, servi par nginx |

Le tag `actuel` utilisé par les manifests K8s est toujours le plus haut numéro de chaque service (`users-service:2`, `tasks-service:3`, `task-manager-front:1`).
