package com.capstoneecho.echo_back.external.llm;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptOverrideRepository extends JpaRepository<PromptOverride, String> {
}
