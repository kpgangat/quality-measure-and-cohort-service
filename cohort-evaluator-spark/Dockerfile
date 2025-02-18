#
# (C) Copyright IBM Corp. 2021, 2021
#
# SPDX-License-Identifier: Apache-2.0
#

# The official ubi8 image from redhat's registry is one of the few accepted base images for Alvearie
ARG BASE_IMAGE=registry.access.redhat.com/ubi8
ARG BASE_IMAGE_VERSION=8.4-206.1626828523

ARG SPARK_VERSION=spark-3.1.2
ARG SPARK_DIST=bin-hadoop3.2
ARG SPARK_UID=185
ARG SPARK_WORK_DIR=/spark-work-dir
ARG SPARK_HOME=/opt/spark


#################
# spark-builder #
#################
FROM ${BASE_IMAGE}:${BASE_IMAGE_VERSION} AS spark-builder

ARG SPARK_HOME
ARG SPARK_VERSION
ARG SPARK_DIST

# Download and extract Spark
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN curl "https://archive.apache.org/dist/spark/${SPARK_VERSION}/${SPARK_VERSION}-${SPARK_DIST}.tgz" | tar -xz
# Move the extracted spark dist to SPARK_HOME
RUN mv "${SPARK_VERSION}-${SPARK_DIST}" "${SPARK_HOME}"
# Move entrypoint.sh to SPARK_HOME
RUN mv "${SPARK_HOME}/kubernetes/dockerfiles/spark/entrypoint.sh" "${SPARK_HOME}"
# Move decom.sh to SPARK_HOME
RUN mv "${SPARK_HOME}/kubernetes/dockerfiles/spark/decom.sh" "${SPARK_HOME}"
# Delete troublesome JSP-2.1 API JAR. It will be upgraded to JSP 2.2 when we copy our dependencies
RUN rm "${SPARK_HOME}/jars/jsp-api-2.1.jar"
# Remove the `tini` use from entrypoint.sh.  Tini is unavailable on Red Hat.
RUN sed -i 's_/usr/bin/tini -s --__g' "${SPARK_HOME}/entrypoint.sh"


##############
# spark-base #
##############
FROM ${BASE_IMAGE}:${BASE_IMAGE_VERSION} as spark-base

ARG SPARK_WORK_DIR
ENV SPARK_WORK_DIR=${SPARK_WORK_DIR}
ARG SPARK_UID
ARG SPARK_HOME
ENV SPARK_HOME=${SPARK_HOME}

# Become root for the Spark installation process
USER root
# Perform a full update of all packages to minimize vulnerabilities
RUN dnf update --nobest -y &&\
    # Install Java 11 with dev tools (jmap, jar, etc.) and procps-ng tooling (top, ps, free, etc.)
    dnf install -y java-11-openjdk-devel procps-ng &&\
    # Clean dnf
    dnf clean all
    
# Copy spark code from builder stage and chown to root
COPY --from=spark-builder --chown=root ${SPARK_HOME} ${SPARK_HOME}

# Add the spark user, create SPARK_WORK_DIR, and set it as the user's home directory
RUN useradd -l -u "${SPARK_UID}" -d "${SPARK_WORK_DIR}" spark

# Configure the runtime for downstream images
WORKDIR ${SPARK_WORK_DIR}
USER spark
ENTRYPOINT [ "/opt/spark/entrypoint.sh" ]


#####################
# spark-application #
#####################
FROM spark-base as spark-application

# Labels - certain labels are required if you want to have
#          a Red Hat certified image (this is not a full set per se)
LABEL maintainer="IBM Quality Measure and Cohort Service Team" \
      description="Quality Measure and Cohort Service Spark Image" \
      name="cohort-evaluator-spark" \
      vendor="Alvearie Open Source by IBM" \
      version="2.0.1" \
      release="2" \
      summary="Quality Measure and Cohort Service Spark Image" \
      description="Quality Measure and Cohort Service Spark Image"

# Temporarily become root to copy over the jars
USER root

# Delete any existing log4j jars.  We run logback over slf4j here.
RUN rm "$SPARK_HOME"/jars/*log4j*jar

# Copy all dependency jars into /opt/spark/jars
COPY --chown=root:root target/lib/*.jar $SPARK_HOME/jars/

# Copy the main jar into the jars directory of spark home
COPY --chown=root:root target/cohort-evaluator-spark-*.jar $SPARK_HOME/jars/

# Go back to the spark user for when a container is created
USER spark

