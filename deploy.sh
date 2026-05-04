#!/usr/bin/env bash
# Déploie projet-task-manager from scratch sur minikube avec Istio (palier 18/20).
# Suppose : Docker, minikube, kubectl installés. Utilisateur loggé sur Docker Hub (ergowmr).
#
# Usage : ./deploy.sh           # build local + push + deploy complet
#         ./deploy.sh --no-push # skip docker push (utile pour itérer)

set -euo pipefail

PUSH=1
[[ "${1:-}" == "--no-push" ]] && PUSH=0

ROOT="$(cd "$(dirname "$0")" && pwd)"
ISTIO_VERSION="1.29.2"
ISTIOCTL="$ROOT/../istio-$ISTIO_VERSION/bin/istioctl.exe"

step() { echo; echo "== $* =="; }

step "1/6 Build + push images"
for entry in "users-service:users-service:2" "tasks-service:tasks-service:3" "frontend:task-manager-front:1"; do
  dir=${entry%%:*}; rest=${entry#*:}; image=${rest%:*}; tag=${rest#*:}
  echo ">> ergowmr/$image:$tag (depuis $dir/)"
  (cd "$ROOT/$dir" && docker build -t "ergowmr/$image:$tag" .)
  if [[ $PUSH -eq 1 ]]; then docker push "ergowmr/$image:$tag"; fi
done

step "2/6 Démarrer minikube + préparer le hostPath du PV postgres"
minikube status >/dev/null 2>&1 || minikube start
minikube ssh -- "sudo mkdir -p /mnt/data && sudo chmod 777 /mnt/data"

step "3/6 Installer Istio (profile default) si pas déjà installé"
if ! kubectl get namespace istio-system >/dev/null 2>&1; then
  if [[ ! -x "$ISTIOCTL" ]]; then
    echo "ERREUR : istioctl introuvable à $ISTIOCTL"
    echo "Télécharge-le depuis https://github.com/istio/istio/releases/tag/$ISTIO_VERSION"
    exit 1
  fi
  "$ISTIOCTL" install --set profile=default -y
fi
kubectl label namespace default istio-injection=enabled --overwrite

step "4/6 Appliquer RBAC + Postgres + Services applicatifs"
kubectl apply -f "$ROOT/k8s/rbac/"
kubectl apply -f "$ROOT/k8s/postgres/"
kubectl rollout status deployment/postgres --timeout=180s
kubectl apply -f "$ROOT/k8s/users-service-deployment.yml" \
              -f "$ROOT/k8s/users-service-service.yml" \
              -f "$ROOT/k8s/tasks-service-deployment.yml" \
              -f "$ROOT/k8s/tasks-service-service.yml" \
              -f "$ROOT/k8s/frontend-deployment.yml" \
              -f "$ROOT/k8s/frontend-service.yml"
for d in users-service tasks-service frontend; do
  kubectl rollout status deployment/$d --timeout=180s
done

step "5/6 Couche Istio : Gateway + VS + DR + mTLS STRICT + AuthorizationPolicy"
kubectl apply -f "$ROOT/k8s/istio/"

step "6/6 État final"
kubectl get pods,svc,gateway,virtualservice,peerauthentication,authorizationpolicy 2>/dev/null
echo
echo "Tunnel  : kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80"
echo "API     : curl -H 'Host: taskmanager.local' http://127.0.0.1:8080/api/tasks/4/full"
echo "Browser : ajouter '127.0.0.1 taskmanager.local' dans le hosts file, puis http://taskmanager.local:8080"
