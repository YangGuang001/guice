/**
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.commands;

import com.google.inject.*;
import com.google.inject.spi.SourceProviders;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import static com.google.inject.internal.Objects.nonNull;

import java.lang.annotation.Annotation;

/**
 * Immutable snapshot of a request to bind a value.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class BindCommand<T> implements Command {

  static {
    SourceProviders.skip(BindCommand.BindingBuilder.class);
  }

  private static final BindTarget<Object> EMPTY_BIND_TARGET = new AbstractTarget<Object>() {
    public ScopedBindingBuilder execute(LinkedBindingBuilder<Object> linkedBindingBuilder) {
      return linkedBindingBuilder;
    }
    public <V> V acceptVisitor(Visitor<Object, V> visitor) {
      return visitor.visitUntargetted();
    }
  };

  private static final BindScoping EMPTY_SCOPING = new AbstractScoping() {
    public void execute(ScopedBindingBuilder scopedBindingBuilder) {
      // do nothing
    }
    public <V> V acceptVisitor(Visitor<V> visitor) {
      return visitor.visitNoScoping();
    }
  };

  private final Object source;
  private Key<T> key;
  @SuppressWarnings({"unchecked"})
  private BindTarget<T> bindTarget = (BindTarget<T>) EMPTY_BIND_TARGET;
  private BindScoping bindScoping = EMPTY_SCOPING;

  BindCommand(Object source, Key<T> key) {
    this.source = nonNull(source, "source");
    this.key = nonNull(key, "key");
  }

  public Object getSource() {
    return source;
  }

  public <V> V acceptVisitor(Visitor<V> visitor) {
    return visitor.visitBind(this);
  }

  public Key<T> getKey() {
    return key;
  }

  public BindTarget<T> getTarget() {
    return bindTarget;
  }

  public BindScoping getScoping() {
    return bindScoping;
  }

  @Override public String toString() {
    return "bind " + key
        + (bindTarget == EMPTY_BIND_TARGET ? "" : (" to " + bindTarget))
        + (bindScoping == EMPTY_SCOPING ? "" : (" in " + bindScoping));
  }

  private static abstract class AbstractTarget<T> implements BindTarget<T> {
    public void execute(ConstantBindingBuilder builder) {
      throw new UnsupportedOperationException();
    }
    public T get() {
      return null;
    }
    public Key<? extends Provider<? extends T>> getProviderKey() {
      return null;
    }
    public Provider<? extends T> getProvider() {
      return null;
    }
    public Key<? extends T> getKey() {
      return null;
    }
  }

  private static abstract class AbstractScoping implements BindScoping {
    public boolean isEagerSingleton() {
      return false;
    }
    public Scope getScope() {
      return null;
    }
    public Class<? extends Annotation> getScopeAnnotation() {
      return null;
    }
  }

  BindingBuilder bindingBuilder(Binder binder) {
    return new BindingBuilder(binder);
  }

  /**
   * Package-private write access to the internal state of this command.
   */
  class BindingBuilder implements AnnotatedBindingBuilder<T> {
    private final Binder binder;

    BindingBuilder(Binder binder) {
      this.binder = binder;
    }

    public LinkedBindingBuilder<T> annotatedWith(
        Class<? extends Annotation> annotationType) {
      nonNull(annotationType, "annotationType");
      assertNotAnnotated();
      key = Key.get(key.getTypeLiteral(), annotationType);
      return this;
    }

    public LinkedBindingBuilder<T> annotatedWith(Annotation annotation) {
      nonNull(annotation, "annotation");
      assertNotAnnotated();
      key = Key.get(key.getTypeLiteral(), annotation);
      return this;
    }

    public ScopedBindingBuilder to(final Class<? extends T> implementation) {
      return to(Key.get(implementation));
    }

    public ScopedBindingBuilder to(
        final TypeLiteral<? extends T> implementation) {
      return to(Key.get(implementation));
    }

    public ScopedBindingBuilder to(final Key<? extends T> targetKey) {
      nonNull(targetKey, "targetKey");
      assertNoTarget();
      bindTarget = new AbstractTarget<T>() {
        public ScopedBindingBuilder execute(LinkedBindingBuilder<T> linkedBindingBuilder) {
          return linkedBindingBuilder.to(targetKey);
        }
        @Override public Key<? extends T> getKey() {
          return targetKey;
        }
        public <V> V acceptVisitor(Visitor<T, V> visitor) {
          return visitor.visitToKey(targetKey);
        }
        @Override public String toString() {
          return String.valueOf(targetKey);
        }
      };
      return this;
    }

    public void toInstance(final T instance) {
      // might someday want to tolerate null here, probably by setting up a
      // Provider<null> rather than trying to distinguish between null and
      // not set
      nonNull(instance, "instance");

      assertNoTarget();
      bindTarget = new AbstractTarget<T>() {
        public ScopedBindingBuilder execute(LinkedBindingBuilder<T> linkedBindingBuilder) {
          linkedBindingBuilder.toInstance(instance);
          return null;
        }
        @Override public T get() {
          return instance;
        }
        public <V> V acceptVisitor(Visitor<T, V> visitor) {
          return visitor.visitToInstance(instance);
        }
        @Override public String toString() {
          return "instance " + instance;
        }
      };
    }

    public ScopedBindingBuilder toProvider(final Provider<? extends T> provider) {
      nonNull(provider, "provider");
      assertNoTarget();
      bindTarget = new AbstractTarget<T>() {
        public ScopedBindingBuilder execute(LinkedBindingBuilder<T> linkedBindingBuilder) {
          return linkedBindingBuilder.toProvider(provider);
        }
        @Override public Provider<? extends T> getProvider() {
          return provider;
        }
        public <V> V acceptVisitor(Visitor<T, V> visitor) {
          return visitor.visitToProvider(provider);
        }
        @Override public String toString() {
          return "provider " + provider;
        }
      };
      return this;
    }

    public ScopedBindingBuilder toProvider(
        Class<? extends Provider<? extends T>> providerType) {
      return toProvider(Key.get(providerType));
    }

    public ScopedBindingBuilder toProvider(
        final Key<? extends Provider<? extends T>> providerKey) {
      nonNull(providerKey, "providerKey");
      assertNoTarget();
      bindTarget = new AbstractTarget<T>() {
        public ScopedBindingBuilder execute(LinkedBindingBuilder<T> linkedBindingBuilder) {
          return linkedBindingBuilder.toProvider(providerKey);
        }
        @Override public Key<? extends Provider<? extends T>> getProviderKey() {
          return providerKey;
        }
        public <V> V acceptVisitor(Visitor<T, V> visitor) {
          return visitor.visitToProviderKey(providerKey);
        }
        @Override public String toString() {
          return "provider " + providerKey;
        }
      };
      return this;
    }

    public void in(final Class<? extends Annotation> scopeAnnotation) {
      nonNull(scopeAnnotation, "scopeAnnotation");
      assertNoScope();

      bindScoping = new AbstractScoping() {
        public void execute(ScopedBindingBuilder scopedBindingBuilder) {
          scopedBindingBuilder.in(scopeAnnotation);
        }
        @Override public Class<? extends Annotation> getScopeAnnotation() {
          return scopeAnnotation;
        }
        public <V> V acceptVisitor(Visitor<V> visitor) {
          return visitor.visitScopeAnnotation(scopeAnnotation);
        }
        @Override public String toString() {
          return scopeAnnotation.getName();
        }
      };
    }

    public void in(final Scope scope) {
      nonNull(scope, "scope");
      assertNoScope();
      bindScoping = new AbstractScoping() {

        public void execute(ScopedBindingBuilder scopedBindingBuilder) {
          scopedBindingBuilder.in(scope);
        }
        @Override public Scope getScope() {
          return scope;
        }
        public <V> V acceptVisitor(Visitor<V> visitor) {
          return visitor.visitScope(scope);
        }
        @Override public String toString() {
          return String.valueOf(scope);
        }
      };
    }

    public void asEagerSingleton() {
      assertNoScope();
      bindScoping = new AbstractScoping() {
        public void execute(ScopedBindingBuilder scopedBindingBuilder) {
          scopedBindingBuilder.asEagerSingleton();
        }
        @Override public boolean isEagerSingleton() {
          return true;
        }
        public <V> V acceptVisitor(Visitor<V> visitor) {
          return visitor.visitEagerSingleton();
        }
        @Override public String toString() {
          return "eager singleton";
        }
      };
    }

    static final String IMPLEMENTATION_ALREADY_SET
        = "Implementation is set more than once.";
    static final String SINGLE_INSTANCE_AND_SCOPE = "Setting the scope is not"
        + " permitted when binding to a single instance.";
    static final String SCOPE_ALREADY_SET = "Scope is set more than once.";
    static final String ANNOTATION_ALREADY_SPECIFIED = "More than one annotation"
        + " is specified for this binding.";

    private void assertNoTarget() {
      if (bindTarget != EMPTY_BIND_TARGET) {
        binder.addError(IMPLEMENTATION_ALREADY_SET);
      }
    }

    private void assertNotAnnotated() {
      if (BindCommand.this.key.getAnnotationType() != null) {
        binder.addError(ANNOTATION_ALREADY_SPECIFIED);
      }
    }

    private void assertNoScope() {
      // Scoping isn't allowed when we have only one instance.
      if (bindTarget.get() != null) {
        binder.addError(SINGLE_INSTANCE_AND_SCOPE);
        return;
      }

      if (bindScoping != EMPTY_SCOPING) {
        binder.addError(SCOPE_ALREADY_SET);
      }
    }
  }
}