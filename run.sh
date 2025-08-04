#!/bin/sh
#
# A convenience script that runs examples based on locally built JARs. Usage:
#   mvn clean package
#   ./run.sh <package-name><class-name>
#

APPLICATION=$1
if [ "$APPLICATION" = "" ]; then
  echo "Please specify one of the application class names:"
  echo "  - kody.integration.KodyPaymentService"
  echo "  - samples.KodyPaymentPublisher"
  exit 1
fi
ENV=$2
if [ "$ENV" = "" ]; then
  echo "Please specify environment name (sandbox/production)"
  exit 1
fi

echo "ENV: $ENV"

# For KodyPaymentPublisher (in samples), pass all arguments except the first (class name)
# For other classes like KodyPaymentService, just pass the environment
if [ "$APPLICATION" = "samples.KodyPaymentPublisher" ]; then
    java -cp target/pubsub-java-1.0-SNAPSHOT.jar "$APPLICATION" "$@"
else
    java -cp target/pubsub-java-1.0-SNAPSHOT.jar "$APPLICATION" "$ENV"
fi