/*
Redline Smalltalk is licensed under the MIT License

Redline Smalltalk Copyright (c) 2010 James C. Ladd

Permission is hereby granted, free of charge, to any person obtaining a copy of this software
and associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package st.redline.smalltalk.interpreter;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.util.Stack;

public class Generator implements Opcodes {

	private static final String SUPERCLASS_FULLY_QUALIFIED_NAME = "st/redline/smalltalk/RObject";
	private static final String METHOD_SUPERCLASS_FULLY_QUALIFIED_NAME = "st/redline/smalltalk/RMethod";
	private static final String SEND_METHOD_NAME = "send";
	private static final String SMALLTALK_CLASS = "st/redline/smalltalk/Smalltalk";
	private static final String[] METHOD_DESCRIPTORS = {
			"(Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
			"(Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
			"(Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
			"(Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
			"(Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
			"(Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
			"(Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
			"(Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
			"(Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
			"(Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
			"(Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
			"(Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Lst/redline/smalltalk/RObject;Ljava/lang/String;)Lst/redline/smalltalk/RObject;",
	};
	private static final int MAXIMUM_KEYWORD_ARGUMENTS = 10;

	private Context current = new Context();
	private Stack<Context> contexts = new Stack<Context>();
	private byte[] classBytes;
	private boolean traceOn;

	public Generator(boolean traceOn) {
		this.traceOn = traceOn;
	}

	public void initialize() {
		initialize(traceOn ? tracingClassWriter() : nonTracingClassWriter());
	}

	private ClassWriter nonTracingClassWriter() {
		return new ClassWriter(ClassWriter.COMPUTE_MAXS);
	}

	private ClassWriter tracingClassWriter() {
		return new TracingClassWriter(ClassWriter.COMPUTE_MAXS);
	}

	void initialize(ClassWriter classWriter) {
		current.classWriter = classWriter;
	}

	public void openMethodClass(String className, String packageInternalName, String sourceName) {
		openContext();
		openClass(className, packageInternalName, sourceName, METHOD_SUPERCLASS_FULLY_QUALIFIED_NAME);
	}

	public void openClass(String className, String packageInternalName) {
		openClass(className, packageInternalName, className, SUPERCLASS_FULLY_QUALIFIED_NAME);
	}

	protected void openClass(String className, String packageInternalName, String sourceName, String superclassFullyQualifiedName) {
		rememberNames(className, packageInternalName, sourceName, superclassFullyQualifiedName);
		openClass();
		openInitializeMethod();
	}

	private void openInitializeMethod() {
		current.methodVisitor = current.classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
		current.methodVisitor.visitCode();
		invokeSuperclassInitMethod();
	}

	private void invokeSuperclassInitMethod() {
		current.methodVisitor.visitVarInsn(ALOAD, 0);
		current.methodVisitor.visitMethodInsn(INVOKESPECIAL, current.superclassFullyQualifiedName, "<init>", "()V");
	}

	private void openClass() {
		current.classWriter.visit(V1_5, ACC_PUBLIC + ACC_SUPER, current.fullyQualifiedName, null, current.superclassFullyQualifiedName, null);
		current.classWriter.visitSource(current.sourceName + ".st", null);
	}

	private void rememberNames(String className, String packageInternalName, String sourceName, String superclassFullyQualifiedName) {
		current.className = className;
		current.packageInternalName = packageInternalName;
		current.fullyQualifiedName = packageInternalName.equals("") ? className : packageInternalName + File.separator + className;
		current.sourceName = sourceName;
		current.superclassFullyQualifiedName = superclassFullyQualifiedName;
	}

	public byte[] classBytes() {
		return classBytes;
	}

	public void closeMethodClass() {
		closeClass();
	}

	public void closeClass() {
		closeInitializeMethod();
		current.classWriter.visitEnd();
		classBytes = current.classWriter.toByteArray();
		closeContext();
	}

	protected void openContext() {
		current.storeOn(contexts);
		current = new Context();
		initialize();
	}

	protected void closeContext() {
		current = current.restoreFrom(contexts);
	}

	private void closeInitializeMethod() {
		current.methodVisitor.visitInsn(RETURN);
		current.methodVisitor.visitMaxs(1, 1);
		current.methodVisitor.visitEnd();
	}

	public void classLookup(String className, int line) {
		visitLine(line);
		currentSmalltalkClass();
		current.methodVisitor.visitLdcInsn(className);
		current.methodVisitor.visitMethodInsn(INVOKEVIRTUAL, SMALLTALK_CLASS, "primitiveAt", "(Ljava/lang/String;)Lst/redline/smalltalk/RObject;");
	}

	private void visitLine(int line) {
		Label label = new Label();
		current.methodVisitor.visitLabel(label);
		current.methodVisitor.visitLineNumber(line, label);
	}

	private void currentSmalltalkClass() {
		current.methodVisitor.visitMethodInsn(INVOKESTATIC, SMALLTALK_CLASS, "instance", "()Lst/redline/smalltalk/Smalltalk;");
	}

	public void unarySend(String unarySelector, int line) {
		visitLine(line);
		current.methodVisitor.visitLdcInsn(unarySelector);
		current.methodVisitor.visitMethodInsn(INVOKESTATIC, current.fullyQualifiedName, SEND_METHOD_NAME, METHOD_DESCRIPTORS[0]);
	}

	public void stackPop() {
		current.methodVisitor.visitInsn(POP);
	}

	public void primitiveStringConversion(String string, int line) {
		currentSmalltalkClass();
		current.methodVisitor.visitLdcInsn(string.substring(1, string.length() - 1));  // remove ''
		current.methodVisitor.visitMethodInsn(INVOKEVIRTUAL, SMALLTALK_CLASS, "stringFromPrimitive", "(Ljava/lang/String;)Lst/redline/smalltalk/RObject;");
	}

	public void primitiveSymbolConversion(String symbol, int line) {
		currentSmalltalkClass();
		current.methodVisitor.visitLdcInsn(symbol);
		current.methodVisitor.visitMethodInsn(INVOKEVIRTUAL, SMALLTALK_CLASS, "symbolFromPrimitive", "(Ljava/lang/String;)Lst/redline/smalltalk/RObject;");
	}

	public void keywordSend(String keywordSelector, int countOfArguments, int line) {
		if (countOfArguments > MAXIMUM_KEYWORD_ARGUMENTS)
			throw new IllegalArgumentException("More than " + MAXIMUM_KEYWORD_ARGUMENTS + " keyword arguments!");
		visitLine(line);
		current.methodVisitor.visitLdcInsn(keywordSelector);
		current.methodVisitor.visitMethodInsn(INVOKESTATIC, current.fullyQualifiedName, SEND_METHOD_NAME, METHOD_DESCRIPTORS[countOfArguments]);
	}

	public void binarySend(String binarySelector, int line) {
		visitLine(line);
		current.methodVisitor.visitLdcInsn(binarySelector);
		current.methodVisitor.visitMethodInsn(INVOKESTATIC, current.fullyQualifiedName, SEND_METHOD_NAME, METHOD_DESCRIPTORS[1]);
	}

	static class Context {
		ClassWriter classWriter;
		String className;
		String sourceName;
		String packageInternalName;
		String fullyQualifiedName;
		MethodVisitor methodVisitor;
		String superclassFullyQualifiedName;

		void storeOn(Stack<Context> contexts) {
			contexts.push(this);
		}

		Context restoreFrom(Stack<Context> contexts) {
			if (contexts.isEmpty())
				return this;
			return contexts.pop();
		}
	}
}
