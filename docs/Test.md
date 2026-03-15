### 3000 VU
RAMP UP: 1m
STEADY: 3m
RAM DOWN: 1m

### 5000 VU
RAMP UP: 2m
STEADY 5m
RAM DOWN: 1m

### 7000 VU
RAMP UP: 3m
STEADY: 7m
RAM DOWN: 1m

### 10k VU
RAMP UP: 4m
STEADYL 7m
RAM DOWN: 1m

### 12k VU
RAMP UP: 5m
STEADYL 8m
RAM DOWN: 1m

### Redis Sentinel Failover Test
1. Deploy or refresh Redis with Sentinel enabled:
	```bash
	cd scripts
	./deploy-redis.sh
	```
2. Confirm Redis and Sentinel are healthy:
	```bash
	kubectl get pods -n redis
	kubectl get statefulset -n redis
	kubectl get svc -n redis
	```
3. Record a short URL through the Spring service:
	```bash
	curl -i -X PUT 'http://localhost:30000/?short=failover&long=https://example.com/failover'
	```
4. Warm the cache and verify reads succeed before failover:
	```bash
	curl -i 'http://localhost:30000/failover'
	```
5. Check current pod IPs and current Sentinel master IP:
	```bash
	kubectl get pods -n redis -o wide
	kubectl exec -n redis redis-sentinel-0 -- redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
	```
6. Force a real outage long enough for Sentinel to promote a replica:
	```bash
	kubectl scale statefulset redis-primary -n redis --replicas=0
	sleep 12
	```
7. Verify Sentinel reports a new master IP (should now be one of the replica pod IPs):
	```bash
	kubectl exec -n redis redis-sentinel-0 -- redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
	```
	Expected result: the Sentinel master IP may be different from redis-primary-0 pod IP. After failover, this is correct behavior and means a replica was promoted.
8. Bring the original primary StatefulSet back and let Sentinel reconfigure it as a replica:
	```bash
	kubectl scale statefulset redis-primary -n redis --replicas=1
	kubectl get pods -n redis -w
	```
9. Repeat the redirect request and confirm the app still resolves the short URL after failover:
	```bash
	curl -i 'http://localhost:30000/failover'
	```
10. If the app is running in Kubernetes, restart the Spring deployment only if it was started before Sentinel was deployed:
	```bash
	kubectl rollout restart deployment/spring-deployment
	```