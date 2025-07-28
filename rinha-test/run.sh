#!/bin/bash

docker compose -f docker-compose-payment-processor up -d
docker compose up -d

k6 run -e MAX_REQUESTS=550 rinha.js
#k6 run -e MAX_REQUESTS=550 rinha.js
#k6 run -e MAX_REQUESTS=550 rinha.js

docker compose -f docker-compose-payment-processor down
docker compose down
