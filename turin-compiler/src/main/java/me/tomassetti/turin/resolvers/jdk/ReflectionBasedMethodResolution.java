package me.tomassetti.turin.resolvers.jdk;

import me.tomassetti.turin.compiler.AmbiguousCallException;
import me.tomassetti.jvm.JvmConstructorDefinition;
import me.tomassetti.jvm.JvmType;
import me.tomassetti.turin.definitions.TypeDefinition;
import me.tomassetti.turin.resolvers.SymbolResolver;
import me.tomassetti.turin.parser.ast.expressions.ActualParam;
import me.tomassetti.turin.parser.ast.typeusage.*;
import me.tomassetti.turin.symbols.FormalParameterSymbol;
import me.tomassetti.turin.typesystem.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

class ReflectionBasedMethodResolution {

    private static class MethodOrConstructor {
        private Constructor constructor;
        private Method method;

        public MethodOrConstructor(Constructor constructor) {
            this.constructor = constructor;
        }

        public MethodOrConstructor(Method method) {
            this.method = method;
        }

        public int getParameterCount() {
            if (method != null) {
                return method.getParameterCount();
            } else {
                return constructor.getParameterCount();
            }
        }

        public Class<?> getParameterType(int i) {
            if (method != null) {
                return method.getParameterTypes()[i];
            } else {
                return constructor.getParameterTypes()[i];
            }
        }
    }

    public static List<FormalParameterSymbol> formalParameters(Constructor constructor, SymbolResolver resolver) {
        List<FormalParameterSymbol> formalParameters = new ArrayList<>();
        int i=0;
        for (Type type : constructor.getGenericParameterTypes()) {
            formalParameters.add(new FormalParameterSymbol(toTypeUsage(type, Collections.emptyMap(), resolver), constructor.getParameters()[i].getName()));
            i++;
        }
        return formalParameters;
    }

    public static List<FormalParameterSymbol> formalParameters(Method method, Map<String, TypeUsage> typeVariables, SymbolResolver resolver) {
        List<FormalParameterSymbol> formalParameters = new ArrayList<>();
        int i=0;
        for (Type type : method.getGenericParameterTypes()) {
            formalParameters.add(new FormalParameterSymbol(toTypeUsage(type, typeVariables, resolver), method.getParameters()[i].getName()));
            i++;
        }
        return formalParameters;
    }

    public static TypeUsage toTypeUsage(Type type, Map<String, TypeUsage> typeVariables, SymbolResolver resolver) {
        if (type instanceof Class) {
            Class clazz = (Class)type;
            if (clazz.getCanonicalName().equals(void.class.getCanonicalName())) {
                return new VoidTypeUsageNode();
            }
            if (clazz.isPrimitive()) {
                return PrimitiveTypeUsage.getByName(clazz.getName());
            }
            if (clazz.isArray()) {
                return new ArrayTypeUsage(toTypeUsage(clazz.getComponentType(), typeVariables, resolver));
            }
            TypeDefinition typeDefinition = new ReflectionBasedTypeDefinition((Class) type, resolver);
            ReferenceTypeUsage referenceTypeUsage = new ReferenceTypeUsage(typeDefinition);
            return referenceTypeUsage;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            TypeDefinition typeDefinition = new ReflectionBasedTypeDefinition((Class) parameterizedType.getRawType(), resolver);
            List<TypeUsage> typeParams = Arrays.stream(parameterizedType.getActualTypeArguments()).map((pt) -> toTypeUsage(pt, typeVariables, resolver)).collect(Collectors.toList());
            return new ReferenceTypeUsage(typeDefinition, typeParams);
        } else if (type instanceof TypeVariable) {
            TypeVariable typeVariable = (TypeVariable)type;
            return toTypeUsage(typeVariable, typeVariables, resolver);
        } else {
            throw new UnsupportedOperationException(type.getClass().getCanonicalName());
        }
    }

    public static TypeUsage toTypeUsage(TypeVariable typeVariable, Map<String, TypeUsage> typeVariables, SymbolResolver resolver) {
        TypeVariableUsage.GenericDeclaration genericDeclaration = null;
        List<TypeUsage> bounds = Arrays.stream(typeVariable.getBounds()).map((b)->toTypeUsage(b, typeVariables, resolver)).collect(Collectors.toList());
        if (typeVariable.getGenericDeclaration() instanceof Class) {
            if (typeVariables.containsKey(typeVariable.getName())) {
                return typeVariables.get(typeVariable.getName());
            } else {
                Class c = (Class)typeVariable.getGenericDeclaration();
                //throw new UnsolvedSymbolException("Cannot solve type variable " + typeVariable.getName());
                return new ConcreteTypeVariableUsage(TypeVariableUsage.GenericDeclaration.onClass(c.getCanonicalName()), typeVariable.getName(), bounds);
            }
        } else if (typeVariable.getGenericDeclaration() instanceof Method) {
            Method method = (Method)typeVariable.getGenericDeclaration();
            genericDeclaration = TypeVariableUsage.GenericDeclaration.onMethod(method.getDeclaringClass().getCanonicalName(), ReflectionTypeDefinitionFactory.toMethodDefinition(method).getDescriptor());
        } else {
            throw new UnsupportedOperationException(typeVariable.getGenericDeclaration().getClass().getCanonicalName());
        }
        return new ConcreteTypeVariableUsage(genericDeclaration, typeVariable.getName(), bounds);
    }

    public static JvmConstructorDefinition findConstructorAmong(List<JvmType> argsTypes, SymbolResolver resolver, List<Constructor> constructors) {
        List<MethodOrConstructor> methodOrConstructors = constructors.stream().map((m)->new MethodOrConstructor(m)).collect(Collectors.toList());
        MethodOrConstructor methodOrConstructor = findMethodAmong(argsTypes, resolver, methodOrConstructors, "constructor");
        if (methodOrConstructor == null) {
            throw new RuntimeException("unresolved constructor for " + argsTypes);
        }
        return ReflectionTypeDefinitionFactory.toConstructorDefinition(methodOrConstructor.constructor);
    }

    public static Constructor findConstructorAmongActualParams(List<ActualParam> argsTypes, SymbolResolver resolver, List<Constructor> constructors) {
        List<MethodOrConstructor> methodOrConstructors = constructors.stream().map((m)->new MethodOrConstructor(m)).collect(Collectors.toList());
        MethodOrConstructor methodOrConstructor = findMethodAmongActualParams(argsTypes, resolver, methodOrConstructors, "constructor");
        if (methodOrConstructor == null) {
            throw new RuntimeException("unresolved constructor for " + argsTypes);
        }
        return methodOrConstructor.constructor;
    }

    public static Method findMethodAmong(String name, List<JvmType> argsTypes, SymbolResolver resolver, boolean staticContext, List<Method> methods) {
        List<MethodOrConstructor> methodOrConstructors = methods.stream()
                .filter((m) -> Modifier.isStatic(m.getModifiers()) == staticContext)
                .filter((m) -> m.getName().equals(name))
                .map((m) -> new MethodOrConstructor(m)).collect(Collectors.toList());
        MethodOrConstructor methodOrConstructor = findMethodAmong(argsTypes, resolver, methodOrConstructors, name);
        if (methodOrConstructor == null) {
            throw new RuntimeException("unresolved method " + name + " for " + argsTypes);
        }
        return methodOrConstructor.method;
    }

    public static Optional<Method> findMethodAmongActualParams(String name, List<ActualParam> argsTypes, SymbolResolver resolver, boolean staticContext, List<Method> methods) {
        List<MethodOrConstructor> methodOrConstructors = methods.stream()
                .filter((m) -> Modifier.isStatic(m.getModifiers()) == staticContext)
                .filter((m) -> m.getName().equals(name))
                .map((m) -> new MethodOrConstructor(m)).collect(Collectors.toList());
        MethodOrConstructor methodOrConstructor = findMethodAmongActualParams(argsTypes, resolver, methodOrConstructors, name);
        if (methodOrConstructor == null) {
            return Optional.empty();
        }
        return Optional.of(methodOrConstructor.method);
    }

    private static MethodOrConstructor findMethodAmong(List<JvmType> argsTypes, SymbolResolver resolver, List<MethodOrConstructor> methods, String desc) {
        List<MethodOrConstructor> suitableMethods = new ArrayList<>();
        for (MethodOrConstructor method : methods) {
            if (method.getParameterCount() == argsTypes.size()) {
                boolean match = true;
                for (int i = 0; i < argsTypes.size(); i++) {
                    TypeUsage actualType = TypeUsageNode.fromJvmType(argsTypes.get(i), resolver.getRoot(), Collections.emptyMap());
                    TypeUsage formalType = ReflectionTypeDefinitionFactory.toTypeUsage(method.getParameterType(i), resolver);
                    if (!actualType.canBeAssignedTo(formalType)) {
                        match = false;
                    }
                }
                if (match) {
                    suitableMethods.add(method);
                }
            }
        }

        if (suitableMethods.size() == 0) {
            return null;
        } else if (suitableMethods.size() == 1) {
            return suitableMethods.get(0);
        } else {
            return findMostSpecific(suitableMethods, new AmbiguousCallException(null, desc, argsTypes), argsTypes, resolver);
        }
    }

    private static MethodOrConstructor findMethodAmongActualParams(List<ActualParam> argsTypes, SymbolResolver resolver, List<MethodOrConstructor> methods, String desc) {
        List<MethodOrConstructor> suitableMethods = new ArrayList<>();
        for (MethodOrConstructor method : methods) {
            if (method.getParameterCount() == argsTypes.size()) {
                boolean match = true;
                for (int i = 0; i < argsTypes.size(); i++) {
                    TypeUsage actualType = argsTypes.get(i).getValue().calcType();
                    TypeUsage formalType = ReflectionTypeDefinitionFactory.toTypeUsage(method.getParameterType(i), resolver);
                    if (!actualType.canBeAssignedTo(formalType)) {
                        match = false;
                    }
                }
                if (match) {
                    suitableMethods.add(method);
                }
            }
        }

        if (suitableMethods.size() == 0) {
            return null;
        } else if (suitableMethods.size() == 1) {
            return suitableMethods.get(0);
        } else {
            return findMostSpecific(suitableMethods,
                    new AmbiguousCallException(null, argsTypes, desc),
                    argsTypes.stream().map((ap)->ap.getValue().calcType().jvmType()).collect(Collectors.toList()),
                    resolver);
        }
    }

    private static MethodOrConstructor findMostSpecific(List<MethodOrConstructor> methods, AmbiguousCallException exceptionToThrow,
                                                        List<JvmType> argsTypes, SymbolResolver resolver) {
        MethodOrConstructor winningMethod = methods.get(0);
        for (MethodOrConstructor other : methods.subList(1, methods.size())) {
            if (isTheFirstMoreSpecific(winningMethod, other, argsTypes, resolver)) {
            } else if (isTheFirstMoreSpecific(other, winningMethod, argsTypes, resolver)) {
                winningMethod = other;
            } else if (!isTheFirstMoreSpecific(winningMethod, other, argsTypes, resolver)) {
                // neither is more specific
                throw exceptionToThrow;
            }
        }
        return winningMethod;
    }

    private static boolean isTheFirstMoreSpecific(MethodOrConstructor first, MethodOrConstructor second,
                                                  List<JvmType> argsTypes, SymbolResolver resolver) {
        boolean atLeastOneParamIsMoreSpecific = false;
        if (first.getParameterCount() != second.getParameterCount()) {
            throw new IllegalArgumentException();
        }
        for (int i=0;i<first.getParameterCount();i++){
            Class<?> paramFirst = first.getParameterType(i);
            Class<?> paramSecond = second.getParameterType(i);
            if (isTheFirstMoreSpecific(paramFirst, paramSecond, argsTypes.get(i), resolver)) {
                atLeastOneParamIsMoreSpecific = true;
            } else if (isTheFirstMoreSpecific(paramSecond, paramFirst, argsTypes.get(i), resolver)) {
                return false;
            }
        }

        return atLeastOneParamIsMoreSpecific;
    }

    private static boolean isTheFirstMoreSpecific(Class<?> firstType, Class<?> secondType, JvmType targetType, SymbolResolver resolver) {
        boolean firstIsPrimitive = firstType.isPrimitive();
        boolean secondIsPrimitive = secondType.isPrimitive();
        boolean targetTypeIsPrimitive = targetType.isPrimitive();

        // it is a match or a primitive promotion
        if (targetTypeIsPrimitive && firstIsPrimitive && !secondIsPrimitive) {
            return true;
        }
        if (targetTypeIsPrimitive && !firstIsPrimitive && secondIsPrimitive) {
            return false;
        }

        if (firstType.isPrimitive() || firstType.isArray()) {
            return false;
        }
        if (secondType.isPrimitive() || secondType.isArray()) {
            return false;
        }
        // TODO consider generic parameters?
        ReflectionBasedTypeDefinition firstDef = new ReflectionBasedTypeDefinition(firstType, resolver);
        ReflectionBasedTypeDefinition secondDef = new ReflectionBasedTypeDefinition(secondType, resolver);
        TypeUsage firstTypeUsage = new ReferenceTypeUsage(firstDef);
        TypeUsage secondTypeUsage = new ReferenceTypeUsage(secondDef);
        return firstTypeUsage.canBeAssignedTo(secondTypeUsage) && !secondTypeUsage.canBeAssignedTo(firstTypeUsage);
    }

}
