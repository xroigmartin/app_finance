package com.xroig.finance.investments.infrastructure.web;

import com.xroig.finance.investments.application.port.CreateSecurity;
import com.xroig.finance.investments.application.port.CreateSecurity.CreateSecurityCommand;
import com.xroig.finance.investments.application.port.DeleteSecurity;
import com.xroig.finance.investments.application.port.FindSecurities;
import com.xroig.finance.investments.application.port.UpdateSecurity;
import com.xroig.finance.investments.application.port.UpdateSecurity.UpdateSecurityCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Inbound web adapter for the instrument catalogue (§6). Thin by design: DTOs in,
 * inbound ports out. The duplicate ISIN+currency identity and the RN-5 deletion
 * guard surface as 409 through {@code shared.web.DomainExceptionHandler}.
 */
@RestController
@RequestMapping("/api/investments/securities")
public class SecurityController {

    private final FindSecurities findSecurities;
    private final CreateSecurity createSecurity;
    private final UpdateSecurity updateSecurity;
    private final DeleteSecurity deleteSecurity;

    public SecurityController(FindSecurities findSecurities, CreateSecurity createSecurity,
                              UpdateSecurity updateSecurity, DeleteSecurity deleteSecurity) {
        this.findSecurities = findSecurities;
        this.createSecurity = createSecurity;
        this.updateSecurity = updateSecurity;
        this.deleteSecurity = deleteSecurity;
    }

    @GetMapping
    public List<SecurityResponse> findAll() {
        return findSecurities.all().stream().map(SecurityResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SecurityResponse create(@Valid @RequestBody SecurityRequest request) {
        return SecurityResponse.from(createSecurity.create(new CreateSecurityCommand(
                request.isin(), request.currency(), request.name(),
                request.ticker(), request.type(), request.exchange(), request.figi())));
    }

    @PutMapping("/{id}")
    public SecurityResponse update(@PathVariable Long id, @Valid @RequestBody SecurityRequest request) {
        return SecurityResponse.from(updateSecurity.update(id, new UpdateSecurityCommand(
                request.name(), request.ticker(), request.type(), request.exchange(), request.figi())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteSecurity.delete(id);
    }
}
