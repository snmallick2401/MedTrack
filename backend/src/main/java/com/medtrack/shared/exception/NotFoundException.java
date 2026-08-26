package com.medtrack.shared.exception; public class NotFoundException extends DomainException { public NotFoundException(String resource){super("RESOURCE_NOT_FOUND",resource+" was not found");} }
