/* Redline Smalltalk, Copyright (c) James C. Ladd. All rights reserved. See LICENSE in the root of this distribution */
package st.redline;

// Provides a simple primitive object that knows how to:
// - perform a message
// - hold a java value object
// - hold 'n' + 1 primitive objects, one is class object
// Typically you don't create instances of PrimObject directly, instead you ask
// a PrimObjectClass for an instance of the class it represents by sending it the 'new' message.
// A PrimObjectClass instance is obtained via its MetaClass, see PrimObjectMetaclass.

import st.redline.bootstrap.AtSelectorPutMethod;
import st.redline.bootstrap.CreateSubclassMethod;
import st.redline.bootstrap.InstanceVariableNamesMethod;
import st.redline.compiler.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

public class PrimObject {

	public static final ThreadLocal<Stack<String>> PACKAGE_REGISTRY = new ThreadLocal<Stack<String>>();
	static {
		PACKAGE_REGISTRY.set(new Stack<String>());
	}
	public static final Map<String, PrimObject> CLASSES = new ConcurrentHashMap<String, PrimObject>();
	static final Map<String, PrimObject> INTERNED_SYMBOLS = new ConcurrentHashMap<String, PrimObject>();
	static final Map<String, PrimObject> BLOCKS = new ConcurrentHashMap<String, PrimObject>();

	static PrimObject NIL = null;
	static PrimObject TRUE = null;
	static PrimObject FALSE = null;

	static final int DEFAULT_ATTRIBUTE_COUNT = 1;  // default attribute is class
	static final int CLASS_INDEX = 0;
	static final PrimObject BASIC_DOES_NOT_UNDERSTAND = new PrimBasicDoesNotUnderstand();
	static final PrimObject PRIM_NIL = new PrimObject();

	static boolean BOOTSTRAPPING = false;

	Object javaValue = null;
	PrimObject[] attributes;

	public PrimObject() {
		this(0);
	}

	PrimObject(int basicSize) {
		attributes = new PrimObject[basicSize + DEFAULT_ATTRIBUTE_COUNT];
		initialize();
	}

	public PrimObject block(String name) {
		if (BLOCKS.containsKey(name))
			return createBlockInstance(BLOCKS.get(name));
		Block block = (Block) SmalltalkClassLoader.BLOCKS_TO_BE_COMPILED.remove(name);
		if (block == null)
			throw new IllegalStateException("Block to be compiled '" + name + "' not found.");
		block.accept(block.analyser());
		try {
			PrimObject newblock = (PrimObject) smalltalkClassLoader().defineClass(block.classBytes()).newInstance();
			BLOCKS.put(name, newblock);
			return newblock;
		} catch (Exception e) {
			throw new RedlineException(e);
		}
	}

	SmalltalkClassLoader smalltalkClassLoader() {
		return (SmalltalkClassLoader) Thread.currentThread().getContextClassLoader();
	}

	static PrimObject createBlockInstance(PrimObject block) {
		try {
			// We don't create a new instance because the context passed to a block contains space for args and temps.
			// Nb: first thing a block's invoke methods does is set the context temporary space.
			return block;
		} catch (Exception e) {
			throw new RedlineException(e);
		}
	}

	public static PrimObject array(int size) {
		List<PrimObject> array = new ArrayList<PrimObject>();
        PrimObject initialElement = BOOTSTRAPPING ? PRIM_NIL : NIL;
        array.add(initialElement); // we add NIL at index 0 because smalltalk indexes start at 1.
        while (array.size() < size + 1)
            array.add(initialElement);
        PrimObject object = instanceOf("Array").with(array);
//        System.out.println("** array ** " + object + " " + array);
        return object;
    }

	public static PrimObject string(Object javaValue) {
		return instanceOf("String").with(javaValue);
	}

	public static PrimObject number(Object javaValue) {
		return instanceOf("Integer").with(Integer.valueOf((String) javaValue));
	}

	public static PrimObject character(Object javaValue) {
		return instanceOf("Character").with(javaValue);
	}

	public static PrimObject symbol(Object javaValue) {
		String symbol = (String) javaValue;
		if (INTERNED_SYMBOLS.containsKey(symbol))
			return INTERNED_SYMBOLS.get(symbol);
		PrimObject primObject = instanceOf("Symbol").with(symbol);
		INTERNED_SYMBOLS.put(symbol, primObject);
		return primObject;
	}

	PrimObject with(Object value) {
		return javaValue(value);
	}

	static PrimObject instanceOf(String type) {
		return BOOTSTRAPPING ? new PrimObject() : PrimObjectMetaclass.METACLASS.resolveObject(type).perform("new");
	}

	public static void dump(Object object) {
		System.out.println("Dump: " + object);
		for (int i = 0; i < ((PrimObject) object).attributes.length; i++)
			System.out.println(i + " " + ((PrimObject) object).attributes[i]);
	}

	public PrimObject variableAt(String name) {
		int index = cls().indexOfVariable(name);
		if (index != 0)
			return attributes[index];
		return resolveObject(name);
	}

	int indexOfVariable(String name) {
		throw new IllegalStateException("Subclass should override.");
	}

	public static PrimObject putAt(PrimObject receiver, PrimObject object, int index) {
//		System.out.println("putAt() " + receiver + " put: " + object + " at: " + index);
		// re-order arguments, convert int to object and send proper message.
		receiver.perform(number(String.valueOf(index)), object, "at:put:");
		return receiver;
	}

	public PrimObject p61(PrimObject receiver, PrimContext context) {
        // at:put:
//        System.out.println("p61() " + receiver + " at: " + context.argumentAt(0).javaValue() + " put: " + context.argumentAt(1));
        int index = (Integer) context.argumentAt(0).javaValue();
        if (index == 0)
            throw new IllegalStateException("Primitive at:put given index of zero.");
        PrimObject object = context.argumentAt(1);
        List<PrimObject> slots = (List<PrimObject>) receiver.javaValue();
        if (slots == null || slots.size() < index)
            throw new IllegalStateException("Primitive at:put given null or insufficient slots.");
        slots.set(index, object);
        return object; // return stored value according to BlueBook.
    }

	public PrimObject p70(PrimObject receiver, PrimContext context) {
		PrimObjectMetaclass aClass = (PrimObjectMetaclass) receiver;
		PrimObject newInstance = new PrimObject(aClass.primInstanceSize());
		newInstance.cls(aClass);
		return newInstance;
	}

	public PrimObject p111(PrimObject receiver, PrimContext context) {
		return receiver.cls();
	}

	public PrimObject p128(PrimObject receiver, PrimContext context) {
		return createSubclass(receiver, context);
	}

	PrimObject createSubclass(PrimObject receiver, PrimContext context) {
		return new CreateSubclassMethod().invoke(receiver, context);
	}

	public PrimObject p129(PrimObject receiver, PrimContext context) {
		return atSelectorPut(receiver, context);
	}

	PrimObject atSelectorPut(PrimObject receiver, PrimContext context) {
		return new AtSelectorPutMethod().invoke(receiver, context);
	}

	public PrimObject p130(PrimObject receiver, PrimContext context) {
		return receiver.superclass();
	}

	public PrimObject p131(PrimObject receiver, PrimContext context) {
		return addInstanceVariableNames(receiver, context);
	}

	PrimObject addInstanceVariableNames(PrimObject receiver, PrimContext context) {
		return new InstanceVariableNamesMethod().invoke(receiver, context);
	}

	public PrimObject p132(PrimObject receiver, PrimContext context) {
		// classInstanceVariableNames: names
		System.err.println("invoke->classInstanceVariableNames: IMPLEMENT ME.");
		return receiver;
	}

	public PrimObject p133(PrimObject receiver, PrimContext context) {
		// classVariableNames: names
		System.err.println("invoke->classVariableNames: IMPLEMENT ME.");
		return receiver;
	}

	public PrimObject p134(PrimObject receiver, PrimContext context) {
		// poolDictionaries: dictionaries
		System.err.println("invoke->poolDictionaries: IMPLEMENT ME.");
		return receiver;
	}

	public PrimObject p135(PrimObject receiver, PrimContext context) {
		// category: name
		// Nb: we don't store category, here for compatibility with older Smalltalk.
		return receiver;
	}

	PrimObject resolveObject(String name) {
//		System.out.println("resolveObject: " + name);
		if (CLASSES.containsKey(name))
			return CLASSES.get(name);
		if (Character.isUpperCase(name.charAt(0))) {
			String fullyQualifiedName = packageFor(name);
			if (fullyQualifiedName != null)
				return resolveObject(fullyQualifiedName);
		}
		// It is expected the loading of an object results in the registering of
		// a Smalltalk class in the class registry (CLASSES).
		PrimObject primObject = loadObject(name);
		if (primObject != null) {
			if (CLASSES.containsKey(name))
				return CLASSES.get(name);
			return primObject;
		}
		throw new IllegalStateException("Line should never be reached.");
	}

	PrimObject loadObject(String name) {
		try {
			return (PrimObject) Class.forName(name, true, classLoader()).newInstance();
		} catch (Exception e) {
			throw RedlineException.withCause(e);
		}
	}

	ClassLoader classLoader() {
		return Thread.currentThread().getContextClassLoader();
	}

	public String packageFor(String name) {
		PrimObject cls = cls();
		if (cls != null)
			return cls.packageFor(name);
		return null;
	}

	// NOTE: Having perform bundle arguments and call perform0 simplifies the call process in the compiler.
	// A future optimisation will be to remove this bundling, as creating an array and setting values is
	// expensive.

	public PrimObject perform(String selector) {
		return perform0(selector);
	}

	public PrimObject perform(PrimObject arg1, String selector) {
		return perform0(selector, arg1);
	}

	public PrimObject perform(PrimObject arg1, PrimObject arg2, String selector) {
		return perform0(selector, arg1, arg2);
	}

	public PrimObject perform(PrimObject arg1, PrimObject arg2, PrimObject arg3, String selector) {
		return perform0(selector, arg1, arg2, arg3);
	}

	public PrimObject perform(PrimObject arg1, PrimObject arg2, PrimObject arg3, PrimObject arg4, String selector) {
		return perform0(selector, arg1, arg2, arg3, arg4);
	}

	public PrimObject perform(PrimObject arg1, PrimObject arg2, PrimObject arg3, PrimObject arg4, PrimObject arg5, String selector) {
		return perform0(selector, arg1, arg2, arg3, arg4, arg5);
	}

	PrimObject perform0(String selector, PrimObject ... arguments) {
		return perform0(attributes[CLASS_INDEX], selector, arguments);
	}

	void initialize() {
		for (int i = 0; i < attributes.length; i++)
			attributes[i] = PRIM_NIL;
	}

	PrimObject perform0(PrimObject foundInClass, String selector, PrimObject ... arguments) {
//		System.out.println("perform0() " + foundInClass + " " + selector);
		PrimObject cls = foundInClass;
		while (!cls.includesSelector(selector))
			cls = cls.superclass();
		return apply(cls.methodFor(selector), cls, selector, arguments);
	}

	PrimObject apply(PrimObject method, PrimObject foundInClass, String selector, PrimObject ... arguments) {
//		System.out.println("apply " + " " + selector + " to " + this);
		return method.invoke(this, new PrimContext(this, foundInClass, selector, arguments));
	}

	protected PrimObject invoke(PrimObject receiver, PrimContext context) {
		return this;
	}

	boolean includesSelector(String selector) {
		return true;
	}

	PrimObject methodFor(String selector) {
		return BASIC_DOES_NOT_UNDERSTAND;
	}

	public PrimObject superclass() {
		return this;
	}

	public Object javaValue() {
		return javaValue;
	}

	public PrimObject javaValue(Object javaValue) {
		this.javaValue = javaValue;
		return this;
	}

	public PrimObject cls() {
		return attributes[CLASS_INDEX];
	}

	PrimObject cls(PrimObject aClass) {
		attributes[CLASS_INDEX] = aClass;
		return this;
	}
}
