package com.motorplatforms.infra.queue;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessedJobRepository extends JpaRepository<ProcessedJob, UUID> {}
