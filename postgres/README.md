# postgres-docker

# Run locally
```
docker build -t termserver-db .

docker run -d -p 5432:5432 --name termserver-db --restart unless-stopped -e ENV=dev termserver-db
```
  
