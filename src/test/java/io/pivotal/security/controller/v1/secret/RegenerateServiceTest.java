package io.pivotal.security.controller.v1.secret;

import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static io.pivotal.security.helper.SpectrumHelper.itThrows;
import static io.pivotal.security.helper.SpectrumHelper.itThrowsWithMessage;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.data.SecretDataService;
import io.pivotal.security.domain.NamedJsonSecret;
import io.pivotal.security.domain.NamedPasswordSecret;
import io.pivotal.security.domain.NamedRsaSecret;
import io.pivotal.security.domain.NamedSshSecret;
import io.pivotal.security.exceptions.EntryNotFoundException;
import io.pivotal.security.exceptions.ParameterizedValidationException;
import io.pivotal.security.request.AccessControlEntry;
import io.pivotal.security.request.BaseSecretGenerateRequest;
import io.pivotal.security.request.PasswordGenerateRequest;
import io.pivotal.security.request.PasswordGenerationParameters;
import io.pivotal.security.request.RsaGenerateRequest;
import io.pivotal.security.request.SecretRegenerateRequest;
import io.pivotal.security.request.SshGenerateRequest;
import io.pivotal.security.audit.AuditRecordBuilder;
import io.pivotal.security.service.GenerateService;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RunWith(Spectrum.class)
public class RegenerateServiceTest {

  private SecretDataService secretDataService;
  private GenerateService generateService;
  private RegenerateService subject;

  private NamedPasswordSecret namedPasswordSecret;
  private NamedSshSecret namedSshSecret;
  private NamedRsaSecret namedRsaSecret;
  private NamedJsonSecret secretOfUnsupportedType;
  private PasswordGenerationParameters expectedParameters;
  private ResponseEntity responseEntity;

  {
    beforeEach(() -> {
      secretDataService = mock(SecretDataService.class);
      generateService = mock(GenerateService.class);
      namedPasswordSecret = mock(NamedPasswordSecret.class);
      namedSshSecret = mock(NamedSshSecret.class);
      namedRsaSecret = mock(NamedRsaSecret.class);

      when(secretDataService.findMostRecent(eq("unsupported")))
          .thenReturn(secretOfUnsupportedType);
      when(generateService
          .performGenerate(
              isA(AuditRecordBuilder.class),
              isA(BaseSecretGenerateRequest.class),
              isA(AccessControlEntry.class)))
          .thenReturn(new ResponseEntity(HttpStatus.OK));
      secretOfUnsupportedType = new NamedJsonSecret();
      subject = new RegenerateService(secretDataService, generateService);
    });

    describe("#performRegenerate", () -> {
      describe("password", () -> {
        beforeEach(() -> {
          when(secretDataService.findMostRecent(eq("password")))
              .thenReturn(namedPasswordSecret);
          SecretRegenerateRequest passwordGenerateRequest = new SecretRegenerateRequest()
              .setName("password");
          expectedParameters = new PasswordGenerationParameters()
              .setExcludeLower(true)
              .setExcludeUpper(true)
              .setLength(20);
          when(namedPasswordSecret.getName()).thenReturn("password");
          when(namedPasswordSecret.getSecretType()).thenReturn("password");
          when(namedPasswordSecret.getGenerationParameters())
              .thenReturn(expectedParameters);

          responseEntity = subject
              .performRegenerate(mock(AuditRecordBuilder.class), passwordGenerateRequest, mock(AccessControlEntry.class));
        });

        describe("when regenerating password", () -> {
          it("should return a 200 status", () -> {
            assertThat(responseEntity.getStatusCode().value(), equalTo(200));
          });

          it("should generate a new password", () -> {
            ArgumentCaptor<BaseSecretGenerateRequest> generateRequestCaptor =
                ArgumentCaptor.forClass(BaseSecretGenerateRequest.class);

            verify(generateService)
                .performGenerate(
                    isA(AuditRecordBuilder.class),
                    generateRequestCaptor.capture(),
                    isA(AccessControlEntry.class));

            PasswordGenerateRequest generateRequest = (PasswordGenerateRequest) generateRequestCaptor
                .getValue();

            assertThat(generateRequest.getName(), equalTo("password"));
            assertThat(generateRequest.getType(), equalTo("password"));
            assertThat(generateRequest.getGenerationParameters(),
                samePropertyValuesAs(expectedParameters));
          });

        });

        describe("when regenerating password not generated by us", () -> {
          beforeEach(() -> {
            when(namedPasswordSecret.getGenerationParameters())
                .thenReturn(null);
          });

          itThrowsWithMessage(
              "it returns an error",
              ParameterizedValidationException.class,
              "error.cannot_regenerate_non_generated_password",
              () -> {
                SecretRegenerateRequest passwordGenerateRequest = new SecretRegenerateRequest()
                    .setName("password");

                responseEntity = subject
                    .performRegenerate(mock(AuditRecordBuilder.class), passwordGenerateRequest, mock(AccessControlEntry.class));
              });
        });
      });

      describe("ssh & rsa", () -> {
        describe("when regenerating ssh", () -> {
          beforeEach(() -> {
            when(secretDataService.findMostRecent(eq("ssh")))
                .thenReturn(namedSshSecret);
            SecretRegenerateRequest sshRegenerateRequest = new SecretRegenerateRequest()
                .setName("ssh");
            when(namedSshSecret.getName()).thenReturn("ssh");
            when(namedSshSecret.getSecretType()).thenReturn("ssh");

            responseEntity = subject
                .performRegenerate(mock(AuditRecordBuilder.class), sshRegenerateRequest, mock(AccessControlEntry.class));
          });

          it("should return a 200 status", () -> {
            assertThat(responseEntity.getStatusCode().value(), equalTo(200));
          });

          it("should generate a new ssh key pair", () -> {
            ArgumentCaptor<BaseSecretGenerateRequest> generateRequestCaptor =
                ArgumentCaptor.forClass(BaseSecretGenerateRequest.class);

            verify(generateService)
                .performGenerate(
                    isA(AuditRecordBuilder.class),
                    generateRequestCaptor.capture(),
                    isA(AccessControlEntry.class));

            SshGenerateRequest generateRequest = (SshGenerateRequest) generateRequestCaptor
                .getValue();

            assertThat(generateRequest.getName(), equalTo("ssh"));
            assertThat(generateRequest.getType(), equalTo("ssh"));
          });
        });

        describe("when regenerating rsa", () -> {
          beforeEach(() -> {
            when(secretDataService.findMostRecent(eq("rsa")))
                .thenReturn(namedRsaSecret);
            SecretRegenerateRequest rsaRegenerateRequest = new SecretRegenerateRequest()
                .setName("rsa");
            when(namedRsaSecret.getName()).thenReturn("rsa");
            when(namedRsaSecret.getSecretType()).thenReturn("rsa");

            responseEntity = subject
                .performRegenerate(mock(AuditRecordBuilder.class), rsaRegenerateRequest, mock(AccessControlEntry.class));
          });

          it("should return a 200 status", () -> {
            assertThat(responseEntity.getStatusCode().value(), equalTo(200));
          });

          it("should generate a new rsa key pair", () -> {
            ArgumentCaptor<BaseSecretGenerateRequest> generateRequestCaptor =
                ArgumentCaptor.forClass(BaseSecretGenerateRequest.class);

            verify(generateService)
                .performGenerate(
                    isA(AuditRecordBuilder.class),
                    generateRequestCaptor.capture(),
                    isA(AccessControlEntry.class));

            RsaGenerateRequest generateRequest = (RsaGenerateRequest) generateRequestCaptor
                .getValue();

            assertThat(generateRequest.getName(), equalTo("rsa"));
            assertThat(generateRequest.getType(), equalTo("rsa"));
          });
        });
      });

      describe("when regenerating a secret that does not exist", () -> {
        itThrows("an exception", EntryNotFoundException.class, () -> {
          SecretRegenerateRequest passwordGenerateRequest = new SecretRegenerateRequest()
              .setName("missing_entry");

          subject.performRegenerate(mock(AuditRecordBuilder.class), passwordGenerateRequest, mock(AccessControlEntry.class));
        });
      });

      describe("when attempting regenerate of non-regeneratable type", () -> {
        itThrows("an exception", ParameterizedValidationException.class, () -> {
          SecretRegenerateRequest passwordGenerateRequest = new SecretRegenerateRequest()
              .setName("unsupported");

          subject.performRegenerate(mock(AuditRecordBuilder.class), passwordGenerateRequest, mock(AccessControlEntry.class));
        });
      });
    });
  }
}
