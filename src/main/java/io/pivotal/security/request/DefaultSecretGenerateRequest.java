package io.pivotal.security.request;

import io.pivotal.security.domain.Encryptor;
import io.pivotal.security.domain.NamedSecret;
import org.apache.commons.lang.NotImplementedException;

public class DefaultSecretGenerateRequest extends BaseSecretGenerateRequest {
  private Object parameters;

  public Object getParameters() {
    return parameters;
  }

  public void setParameters(Object parameters) {
    this.parameters = parameters;
  }

  @Override
  public NamedSecret createNewVersion(NamedSecret existing, String name, Encryptor encryptor) {
    throw new NotImplementedException();
  }
}
