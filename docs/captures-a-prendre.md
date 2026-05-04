# Captures d'écran à prendre pour le rapport

Liste exhaustive avec les commandes exactes. À réaliser avant de toucher au palier 18 (Istio) pour avoir un état stable.

**Convention de nommage** : `NN-zone-titre.png` rangées dans `docs/img/`. Les noms suggérés correspondent aux placeholders du `RAPPORT.md`.

---

## 1. Code source & repo

### 01-arborescence.png
Structure complète du projet. Sur Windows :
```bash
tree /F /A projet-task-manager > docs/arborescence.txt
```
Ou ouvrir VS Code dans `projet-task-manager/`, déplier l'arbre, capturer.

### 02-readme.png
Capture du README rendu (sur GitHub une fois pushé, ou aperçu VS Code).

---

## 2. Docker Hub (palier 10/20)

### 03-dockerhub-users.png
Aller sur https://hub.docker.com/r/ergowmr/users-service — capturer la page (image publique, tags `1`, `2`).

### 04-dockerhub-tasks.png
https://hub.docker.com/r/ergowmr/tasks-service (tags `1`, `2`, `3`).

### 05-dockerhub-front.png
https://hub.docker.com/r/ergowmr/task-manager-front (tag `1`).

### 06-dockerfile-users.png
Capture de `users-service/Dockerfile` ouvert dans l'éditeur (montre le multi-stage `gradle:8.5-jdk21` → `eclipse-temurin:21-jre`).

---

## 3. Cluster Kubernetes (paliers 10 → 16)

### 07-kubectl-get-all.png
```bash
kubectl get all -l 'app in (users-service,tasks-service,frontend,postgres)'
```
Doit montrer : pods (Running), services (ClusterIP/NodePort), deployments (READY 2/2 ou 1/1).

### 08-kubectl-get-ingress.png
```bash
kubectl get ingress
```
Trois ingress : `taskmanager-users-ingress`, `taskmanager-tasks-ingress`, `taskmanager-frontend-ingress`, tous à l'address `192.168.49.2`.

### 09-kubectl-get-pv-pvc.png
```bash
kubectl get pv,pvc
```
PV `postgres-pv-volume` 5Gi RWO, PVC `postgres-pv-claim` Bound.

### 10-kubectl-describe-deployment-tasks.png
```bash
kubectl describe deployment tasks-service | head -40
```
Montre les variables d'environnement (`SPRING_DATASOURCE_URL` → postgres, `USERS_SERVICE_URL` → users-service).

---

## 4. Persistance Postgres (palier 16/20)

### 11-postgres-tables.png

PowerShell :
```powershell
$PG = kubectl get pod -l app=postgres -o jsonpath='{.items[0].metadata.name}'
kubectl exec $PG -- psql -U taskmanager -d taskmanagerdb -c '\dt'
```

bash / Git Bash :
```bash
PG=$(kubectl get pod -l app=postgres -o jsonpath='{.items[0].metadata.name}')
kubectl exec "$PG" -- psql -U taskmanager -d taskmanagerdb -c '\dt'
```

Doit lister `users`, `tasks` (et `smoke_test` si encore là).

### 12-postgres-data.png

PowerShell (réutilise `$PG` de la commande précédente) :
```powershell
kubectl exec $PG -- psql -U taskmanager -d taskmanagerdb -c 'SELECT * FROM users; SELECT id, title, status, assignee_id FROM tasks ORDER BY id;'
```

bash :
```bash
kubectl exec "$PG" -- psql -U taskmanager -d taskmanagerdb -c 'SELECT * FROM users; SELECT id, title, status, assignee_id FROM tasks ORDER BY id;'
```

Données réelles dans les tables.

### 13-secret-configmap.png
```bash
kubectl get secret postgres-secret -o yaml
kubectl get configmap postgres-config -o yaml
```
Montre le découplage credentials (Secret) / config (ConfigMap).

---

## 5. End-to-end via la gateway (paliers 12 / 14)

> **Sur Windows / PowerShell** : `curl` est un alias d'`Invoke-WebRequest` (qui ne comprend pas `-H`/`-d` style curl). Toujours appeler **`curl.exe`** explicitement. Et pour les bodies JSON inline, passer par une variable PowerShell pour éviter le quote-escape (ne jamais essayer en `cmd.exe`, c'est encore pire).

### 14-curl-users.png

PowerShell :
```powershell
curl.exe -H "Host: taskmanager.local" http://127.0.0.1/api/users
```

bash :
```bash
curl -H "Host: taskmanager.local" http://127.0.0.1/api/users
```

### 15-curl-tasks-full.png
**Important** — preuve de l'appel inter-service :

PowerShell :
```powershell
curl.exe -H "Host: taskmanager.local" http://127.0.0.1/api/tasks/4/full
```

bash :
```bash
curl -H "Host: taskmanager.local" http://127.0.0.1/api/tasks/4/full
```

La réponse contient un objet `assignee` enrichi (données venant de users-service via DNS K8s).

### 16-curl-post-then-get.png
Création + lecture pour démontrer le CRUD via la gateway.

PowerShell (body dans une variable pour éviter le quoting) :
```powershell
$body = '{"title":"démo prof","description":"E2E","status":"DOING","assigneeId":3}'
curl.exe -X POST -H "Host: taskmanager.local" -H "Content-Type: application/json" -d $body http://127.0.0.1/api/tasks
curl.exe -H "Host: taskmanager.local" http://127.0.0.1/api/tasks
```

bash :
```bash
curl -X POST -H "Host: taskmanager.local" -H "Content-Type: application/json" -d '{"title":"démo prof","description":"E2E","status":"DOING","assigneeId":3}' http://127.0.0.1/api/tasks
curl -H "Host: taskmanager.local" http://127.0.0.1/api/tasks
```

---

## 6. Front React (bonus, critère #4 présentation)

### 17-front-home.png  ← **DÉJÀ PRISE** (le screenshot que tu m'as envoyé)
Page d'accueil http://taskmanager.local — 2 colonnes Users/Tâches, status colorés, formulaires.

### 18-front-modal-details.png
Cliquer sur "Détails" d'une tâche → modale avec le JSON complet de `/api/tasks/{id}/full`. Montre la réponse enrichie au niveau visuel.

### 19-front-create-task.png
Avant/après création d'une tâche depuis le formulaire → la liste se met à jour, et la même donnée existe dans Postgres (recoupe avec capture 12).

---

## 7. Google Labs (demandés explicitement par le prof)

### 20-google-labs-younes.png
Aller sur le profil Google Cloud Skills Boost de Younes → onglet "Activité" / "Profil public". Capturer la liste des labs validés.

### 21-google-labs-binome.png
Idem pour le binôme (s'il y en a un).

> Le PDF dit : *"des copies d'écran individuelles des Google labs (voir activitée de votre profil)"* — c'est une demande **séparée** du code, à inclure dans le mail au prof.

---

---

## 8. Palier 18/20 — Sécurité (Istio + RBAC)

> **Préalable** : tunnel via `kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80` dans un terminal séparé.

### 22-dockerhub-scout.png
Sur https://hub.docker.com/r/ergowmr/users-service (ou tasks-service), onglet **Tags** : montrer le rapport Docker Scout avec le compteur de CVE par sévérité. Si pas encore activé, va dans Settings du repo → Image vulnerability scanning → Enable, attends quelques minutes le scan, puis capture.

### 23-istio-pods-sidecar.png
```powershell
kubectl get pods -l 'app in (users-service,tasks-service,frontend,postgres)'
```
Tous les pods doivent montrer `READY 2/2` (preuve que le sidecar Envoy est bien injecté).

### 24-istioctl-describe-mtls.png
```powershell
$ISTIOCTL = "C:\Users\ouama\Documents\2026\M2\CLAUDE CODE\DEVOPS\istio-1.29.2\bin\istioctl.exe"
$TPOD = kubectl get pod -l app=tasks-service -o jsonpath='{.items[0].metadata.name}'
& $ISTIOCTL x describe pod $TPOD
```
La sortie doit montrer `Workload mTLS mode: STRICT` et `Applied PeerAuthentication: default-mtls.default`. **C'est la preuve principale de mTLS.**

### 25-mtls-permissive-vs-strict.png
La démo migration mTLS — à faire en deux étapes (la ligne `kubectl apply` change entre les deux).

Étape 1 (PERMISSIVE) :
```powershell
kubectl run nomesh-curl-permissive -n nomesh --image=curlimages/curl --rm -i --restart=Never -- `
  curl -s --max-time 5 http://users-service.default.svc.cluster.local:8080/users
```
→ doit retourner les users.

Étape 2 (STRICT, après `kubectl apply -f k8s/istio/peerauthentication.yml` avec `mode: STRICT`) :
```powershell
kubectl run nomesh-curl-strict -n nomesh --image=curlimages/curl --rm -i --restart=Never -- `
  curl -sv --max-time 5 http://users-service.default.svc.cluster.local:8080/users
```
→ doit montrer `Recv failure: Connection reset by peer`.

Capture les deux sorties côte à côte (ou en une seule capture sur 2 fenêtres).

### 26-rbac-can-i.png
```powershell
foreach ($sa in 'users-service-sa','tasks-service-sa','frontend-sa','postgres-sa') {
  Write-Host "--- $sa ---"
  Write-Host "  list pods       : $(kubectl auth can-i list pods --as=system:serviceaccount:default:$sa)"
  Write-Host "  list secrets    : $(kubectl auth can-i list secrets --as=system:serviceaccount:default:$sa)"
  Write-Host "  delete pods     : $(kubectl auth can-i delete pods --as=system:serviceaccount:default:$sa)"
}
```
Doit montrer : users-service-sa et tasks-service-sa peuvent `list pods` mais pas `list secrets` ou `delete pods` ; frontend-sa et postgres-sa ne peuvent rien.

### 27-rbac-curl-api.png
On exécute le test depuis le pod `users-service` lui-même (qui tourne sous `users-service-sa`) avec le token monté à `/var/run/secrets/kubernetes.io/serviceaccount/`. Sur Windows / PowerShell, deux pièges : le quoting `sh -c '...'` mange les double-quotes, et un script multi-curl piped via stdin peut voir le 2e curl tomber en `HTTP 000 + exit 3` (consommation de stdin résiduel). Solution propre : **2 here-strings, une par requête**.

```powershell
$UPOD = kubectl get pod -l app=users-service -o jsonpath='{.items[0].metadata.name}'

Write-Host "GET /pods :"
@'
TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)
CA=/var/run/secrets/kubernetes.io/serviceaccount/ca.crt
curl -s -o /dev/null -w "HTTP %{http_code}\n" --cacert $CA -H "Authorization: Bearer $TOKEN" https://kubernetes.default.svc/api/v1/namespaces/default/pods
'@ | kubectl exec -i $UPOD -c users-service -- sh

Write-Host "GET /secrets :"
@'
TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)
CA=/var/run/secrets/kubernetes.io/serviceaccount/ca.crt
curl -s -o /dev/null -w "HTTP %{http_code}\n" --cacert $CA -H "Authorization: Bearer $TOKEN" https://kubernetes.default.svc/api/v1/namespaces/default/secrets
'@ | kubectl exec -i $UPOD -c users-service -- sh
```
**Les `'@` finals doivent être en colonne 0** (pas d'indentation), contrainte stricte PowerShell.

Doit afficher `GET /pods : HTTP 200` puis `GET /secrets : HTTP 403`.

### 28-authzpolicy-deny.png
La démo zero-trust : un pod NGINX (frontend) tente d'appeler users-service en direct → refus AuthorizationPolicy.

```powershell
$FPOD = kubectl get pod -l app=frontend -o jsonpath='{.items[0].metadata.name}'
kubectl exec $FPOD -c frontend -- sh -c 'wget -q -O - --timeout=5 http://users-service:8080/users; echo "exit=$?"'
```
Doit afficher `wget: server returned error: HTTP/1.1 403 Forbidden`.

### 29-istio-resources.png
```powershell
kubectl get gateway,virtualservice,destinationrule,peerauthentication,authorizationpolicy
```
Vue d'ensemble de toutes les ressources Istio créées pour le palier 18.

---

## Checklist finale avant envoi du rapport

- [ ] 17 captures palier 10→16 (01 → 19) ✅ déjà prises
- [ ] Captures Google Labs (20 → 21) ⏳ binôme
- [ ] **8 captures palier 18 (22 → 29)** ← à prendre
- [ ] Repo GitHub `ErgoWmr/<nom>` créé et pushé
- [ ] README à jour avec les liens Docker Hub
- [ ] Mail à `benoit.charroux@gmail.com` avec : (1) lien GitHub, (2) le rapport en PDF/Markdown, (3) les captures Google Labs
