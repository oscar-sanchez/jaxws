/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.tools.ws.processor.modeler.annotation;


import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.ArrayType;
import com.sun.mirror.type.DeclaredType;
import com.sun.mirror.type.PrimitiveType;
import com.sun.mirror.type.TypeMirror;

import java.util.ArrayList;
import java.util.Collection;

/**
 *
 * @author  dkohlert
 */
public class TypeMonikerFactory {

    public static TypeMoniker getTypeMoniker(TypeMirror typeMirror) {
        if (typeMirror instanceof PrimitiveType)
            return new PrimitiveTypeMoniker((PrimitiveType)typeMirror);
        else if (typeMirror instanceof ArrayType)
            return new ArrayTypeMoniker((ArrayType)typeMirror);
        else if (typeMirror instanceof DeclaredType)
            return new DeclaredTypeMoniker((DeclaredType)typeMirror);
        return getTypeMoniker(typeMirror.toString());
    }

    public static TypeMoniker getTypeMoniker(String typeName) {
        return new StringMoniker(typeName);
    }

    static class ArrayTypeMoniker implements TypeMoniker {
        private TypeMoniker arrayType;

        public ArrayTypeMoniker(ArrayType type) {
            arrayType = TypeMonikerFactory.getTypeMoniker(type.getComponentType());
        }

        public TypeMirror create(AnnotationProcessorEnvironment apEnv) {
            return apEnv.getTypeUtils().getArrayType(arrayType.create(apEnv));
        }
    }
    static class DeclaredTypeMoniker implements TypeMoniker {
        private String typeDeclName;
        private Collection<TypeMoniker> typeArgs = new ArrayList<TypeMoniker>();

        public DeclaredTypeMoniker(DeclaredType type) {
            typeDeclName = type.getDeclaration().getQualifiedName();
            for (TypeMirror arg : type.getActualTypeArguments())
                typeArgs.add(TypeMonikerFactory.getTypeMoniker(arg));
        }

        public TypeMirror create(AnnotationProcessorEnvironment apEnv) {
            TypeDeclaration typeDecl = apEnv.getTypeDeclaration(typeDeclName);
            TypeMirror[] tmpArgs = new TypeMirror[typeArgs.size()];
            int idx = 0;
            for (TypeMoniker moniker : typeArgs)
                tmpArgs[idx++] = moniker.create(apEnv);

            return apEnv.getTypeUtils().getDeclaredType(typeDecl, tmpArgs);
        }
    }
    static class PrimitiveTypeMoniker implements TypeMoniker {
        private PrimitiveType.Kind kind;

        public PrimitiveTypeMoniker(PrimitiveType type) {
            kind = type.getKind();
        }

        public TypeMirror create(AnnotationProcessorEnvironment apEnv) {
            return apEnv.getTypeUtils().getPrimitiveType(kind);
        }
    }
    static class StringMoniker implements TypeMoniker {
        private String typeName;

        public StringMoniker(String typeName) {
            this.typeName = typeName;
        }

        public TypeMirror create(AnnotationProcessorEnvironment apEnv) {
            return apEnv.getTypeUtils().getDeclaredType(apEnv.getTypeDeclaration(typeName));
        }
    }
}