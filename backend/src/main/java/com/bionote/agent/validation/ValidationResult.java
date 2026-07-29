package com.bionote.agent.validation;

import java.util.List;

public record ValidationResult(boolean valid,List<String> errors){public ValidationResult{errors=errors==null?List.of():List.copyOf(errors);}public static ValidationResult ok(){return new ValidationResult(true,List.of());}}
