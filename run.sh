#!/bin/sh
#
# A convenience script that runs examples based on locally built JARs. Usage:
#   mvn clean package
#   ./run.sh <package-name><class-name>
#

EXAMPLE=$1
if [ "x$EXAMPLE" = "x" ]; then
  echo "Please specify one of the example class names from the package com.salesforce.eventbusclient.example"
  exit -1
fi
ENV=$2
if [ "x$ENV" = "x" ]; then
  echo "Please specify environment name"
  exit -1
fi

echo "ENV: $ENV"

# For KodyPaymentPublisher, pass all arguments except the first (class name)
# For other classes like KodyPaymentSubscriber, just pass the environment
if [ "$EXAMPLE" = "genericpubsub.KodyPaymentPublisher" ]; then
    java -cp target/pubsub-java-1.0-SNAPSHOT.jar $EXAMPLE "$@"
else
    java -cp target/pubsub-java-1.0-SNAPSHOT.jar $EXAMPLE $ENV
fi