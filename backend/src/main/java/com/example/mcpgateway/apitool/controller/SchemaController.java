package com.example.mcpgateway.apitool.controller;

import com.example.mcpgateway.apitool.application.service.SchemaConversionService;
import com.example.mcpgateway.apitool.application.service.SchemaConversionService.InvalidSchemaException;
import com.example.mcpgateway.apitool.application.service.SchemaConversionService.SchemaField;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schema")
public class SchemaController {
    private final SchemaConversionService service;
    public SchemaController(SchemaConversionService service) { this.service = service; }

    @PostMapping("/to-fields")
    List<SchemaField> toFields(@Valid @RequestBody FromSchemaRequest req) {
        return service.fromJsonSchema(req.schemaJson());
    }

    @PostMapping("/from-fields")
    String fromFields(@Valid @RequestBody FromFieldsRequest req) {
        return service.toJsonSchema(req.fields());
    }

    @PostMapping("/validate")
    ValidateResponse validate(@Valid @RequestBody FromSchemaRequest req) {
        try {
            service.validate(req.schemaJson());
            return new ValidateResponse(true, null);
        } catch (InvalidSchemaException e) {
            return new ValidateResponse(false, e.getMessage());
        }
    }

    public record FromSchemaRequest(@NotBlank String schemaJson) {}
    public record FromFieldsRequest(@NotNull List<SchemaField> fields) {}
    public record ValidateResponse(boolean valid, String error) {}
}
