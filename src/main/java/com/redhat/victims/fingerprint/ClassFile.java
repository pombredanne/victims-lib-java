package com.redhat.victims.fingerprint;

/*
 * #%L
 * This file is part of victims-lib.
 * %%
 * Copyright (C) 2013 The Victims Project
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.*;
import org.apache.bcel.util.ByteSequence;
import org.apache.commons.io.IOUtils;

import com.redhat.victims.VictimsConfig;

/**
 * Implements handling of .class byte code for fingerprinting.
 * 
 * @author abn
 * @author gcmurphy
 */
public class ClassFile extends File {

	/**
	 * 
	 * @param bytes
	 *            The file as a byte array.
	 * @param fileName
	 *            The name of the file provided by the stream.
	 * @throws IOException
	 */
	public ClassFile(byte[] bytes, String fileName) throws IOException {
		super(ClassFile.normalize(bytes, fileName), fileName);
	}

	/**
	 * 
	 * @param is
	 *            The file as an input stream.
	 * @param fileName
	 *            The name of the file provided by the stream.
	 * @throws IOException
	 */
	public ClassFile(InputStream is, String fileName) throws IOException {
		this(IOUtils.toByteArray(is), fileName);
	}

	/**
	 * Resolves a constants value from the constant pool to the lowest form it
	 * can be represented by. E.g. String, Integer, Float, etc.
	 * 
	 * @author gcmurphy
	 * @param index
	 * @param cp
	 * @return
	 */
	public static String constantValue(int index, ConstantPool cp) {
		Constant type = cp.getConstant(index);
		if (type != null) {
			switch (type.getTag()) {
			case Constants.CONSTANT_Class:
				ConstantClass cls = (ConstantClass) type;
				return constantValue(cls.getNameIndex(), cp);

			case Constants.CONSTANT_Double:
				ConstantDouble dbl = (ConstantDouble) type;
				return String.valueOf(dbl.getBytes());

			case Constants.CONSTANT_Fieldref:
				ConstantFieldref fieldRef = (ConstantFieldref) type;
				return constantValue(fieldRef.getClassIndex(), cp) + " "
						+ constantValue(fieldRef.getNameAndTypeIndex(), cp);

			case Constants.CONSTANT_Float:
				ConstantFloat flt = (ConstantFloat) type;
				return String.valueOf(flt.getBytes());

			case Constants.CONSTANT_Integer:
				ConstantInteger integer = (ConstantInteger) type;
				return String.valueOf(integer.getBytes());

			case Constants.CONSTANT_InterfaceMethodref:
				ConstantInterfaceMethodref intRef = (ConstantInterfaceMethodref) type;
				return constantValue(intRef.getClassIndex(), cp) + " "
						+ constantValue(intRef.getNameAndTypeIndex(), cp);

			case Constants.CONSTANT_Long:
				ConstantLong lng = (ConstantLong) type;
				return String.valueOf(lng.getBytes());

			case Constants.CONSTANT_Methodref:
				ConstantMethodref methRef = (ConstantMethodref) type;
				return constantValue(methRef.getClassIndex(), cp) + " "
						+ constantValue(methRef.getNameAndTypeIndex(), cp);

			case Constants.CONSTANT_NameAndType:
				ConstantNameAndType nameType = (ConstantNameAndType) type;
				return nameType.getName(cp) + " " + nameType.getSignature(cp);

			case Constants.CONSTANT_String:
				ConstantString str = (ConstantString) type;
				return str.getBytes(cp);

			case Constants.CONSTANT_Utf8:
				ConstantUtf8 utf8 = (ConstantUtf8) type;
				return utf8.getBytes();
			}
		}
		return "";
	}

	/**
	 * Normalizes the bytecode using the supplied constant pool. Essentially all
	 * lookups via index to the constant pool are resolved and inserted in place
	 * with their string value. This is designed to reduce any inconsistencies
	 * between the JDK compiled bytecode that is introduced by adding constants
	 * at different indices to the constant pool.
	 * 
	 * @author gcmurphy
	 * @param bytes
	 * @param cp
	 * @return
	 * @throws IOException
	 */
	public static String formatBytecode(ByteSequence bytes, ConstantPool cp)
			throws IOException {
		StringBuilder buf = new StringBuilder();
		int index, rem, pad, def, low, hi, npair;
		short opcode;
		boolean wide = false;

		while (bytes.available() > 0) {
			opcode = (short) bytes.readUnsignedByte();
			buf.append(opcode);

			switch (opcode) {
			case Constants.TABLESWITCH:

				pad = 0;
				if ((rem = bytes.getIndex() % 4) != 0) {
					pad = 4 - rem;
				}

				for (int i = 0; i < pad; i++) {
					buf.append(bytes.readByte());
				}

				def = bytes.readInt();
				buf.append(def);

				low = bytes.readInt();
				buf.append(low);

				hi = bytes.readInt();
				buf.append(hi);

				for (int i = 0; i < (hi - low + 1); i++) {
					buf.append(bytes.readInt());
				}

				break;

			case Constants.LOOKUPSWITCH:
				pad = 0;
				if ((rem = bytes.getIndex() % 4) != 0) {
					pad = 4 - rem;
				}

				for (int i = 0; i < pad; i++) {
					buf.append(bytes.readByte());
				}

				def = bytes.readInt();
				buf.append(def);

				npair = bytes.readInt();
				buf.append(npair);

				for (int i = 0; i < npair; i++) {
					buf.append(bytes.readInt()); // match
					buf.append(bytes.readInt()); // jump
				}

				break;

			case Constants.GOTO:
			case Constants.IFEQ:
			case Constants.IFGE:
			case Constants.IFGT:
			case Constants.IFLE:
			case Constants.IFLT:
			case Constants.JSR:
			case Constants.IFNE:
			case Constants.IFNONNULL:
			case Constants.IFNULL:
			case Constants.IF_ACMPEQ:
			case Constants.IF_ACMPNE:
			case Constants.IF_ICMPEQ:
			case Constants.IF_ICMPGE:
			case Constants.IF_ICMPGT:
			case Constants.IF_ICMPLE:
			case Constants.IF_ICMPLT:
			case Constants.IF_ICMPNE:
				buf.append(bytes.readShort());
				break;

			case Constants.GOTO_W:
			case Constants.JSR_W:
				buf.append(bytes.readInt());
				break;

			case Constants.ALOAD:
			case Constants.ASTORE:
			case Constants.DLOAD:
			case Constants.DSTORE:
			case Constants.FLOAD:
			case Constants.FSTORE:
			case Constants.ILOAD:
			case Constants.ISTORE:
			case Constants.LLOAD:
			case Constants.LSTORE:
			case Constants.NEWARRAY:
			case Constants.RET:
				if (wide) {
					buf.append(bytes.readUnsignedShort());
					wide = false;
				} else {
					buf.append(bytes.readUnsignedByte());
				}
				break;

			case Constants.WIDE:
				wide = true;
				break;

			case Constants.GETFIELD:
			case Constants.GETSTATIC:
			case Constants.PUTFIELD:
			case Constants.PUTSTATIC:
			case Constants.NEW:
			case Constants.CHECKCAST:
			case Constants.INSTANCEOF:
			case Constants.INVOKESPECIAL:
			case Constants.INVOKESTATIC:
			case Constants.INVOKEVIRTUAL:
			case Constants.LDC_W:
			case Constants.LDC2_W:
			case Constants.ANEWARRAY:
				index = bytes.readUnsignedShort();
				buf.append(constantValue(index, cp));
				break;

			case Constants.LDC:
				index = bytes.readUnsignedByte();
				buf.append(constantValue(index, cp));
				break;

			case Constants.MULTIANEWARRAY:
				index = bytes.readUnsignedShort();
				buf.append(constantValue(index, cp));
				buf.append(bytes.readUnsignedByte());
				break;

			case Constants.IINC:
				if (wide) {
					buf.append(bytes.readUnsignedShort());
					buf.append(bytes.readShort());

				} else {
					buf.append(bytes.readUnsignedByte());
					buf.append(bytes.readByte());
				}
				break;

			case Constants.INVOKEINTERFACE:
				index = bytes.readUnsignedShort();
				buf.append(constantValue(index, cp));
				buf.append(bytes.readUnsignedByte());
				buf.append(bytes.readUnsignedByte());
				break;

			default:
				if (Constants.NO_OF_OPERANDS[opcode] > 0) {
					for (int i = 0; i < Constants.TYPE_OF_OPERANDS[opcode].length; i++) {
						switch (Constants.TYPE_OF_OPERANDS[opcode][i]) {
						case Constants.T_BYTE:
							buf.append(bytes.readUnsignedByte());
							break;

						case Constants.T_SHORT:
							buf.append(bytes.readUnsignedByte());
							buf.append(bytes.readUnsignedByte());
							break;

						case Constants.T_INT:
							buf.append(bytes.readUnsignedByte());
							buf.append(bytes.readUnsignedByte());
							buf.append(bytes.readUnsignedByte());
							buf.append(bytes.readUnsignedByte());
							break;

						default:
							break;
						}
					}
				}
			}
		}

		return buf.toString();
	}

	/**
	 * The driving function that normalizes given byte code.
	 * 
	 * @param bytes
	 *            The input class as a byte array.
	 * @param fileName
	 *            The name of the file.
	 * @return The normalized bytecode as a byte array.
	 * @throws IOException
	 */
	public static byte[] normalize(byte[] bytes, String fileName)
			throws IOException {
		ByteArrayInputStream is = new ByteArrayInputStream(bytes);
		ClassParser parser = new ClassParser(is, fileName);
		StringBuffer buf = new StringBuffer();
		JavaClass klass = parser.parse();
		ConstantPool cpool = klass.getConstantPool();
		// source file
		buf.append(klass.getSourceFileName());
		// access flags
		buf.append(String.valueOf(klass.getAccessFlags()));
		// this class
		buf.append(constantValue(klass.getClassNameIndex(), cpool));
		// super class (extends)
		buf.append(constantValue(klass.getSuperclassNameIndex(), cpool));
		// interfaces (implements)
		for (int index : klass.getInterfaceIndices()) {
			// implemented interface name
			buf.append(constantValue(index, cpool));
		}
		// fields
		for (Field f : klass.getFields()) {
			// access flags
			buf.append(String.valueOf(f.getAccessFlags()));
			// name
			buf.append(constantValue(f.getNameIndex(), cpool));
			// signature
			buf.append(constantValue(f.getSignatureIndex(), cpool));
			// value
			if (f.getConstantValue() != null) {
				int index = f.getConstantValue().getConstantValueIndex();
				buf.append(constantValue(index, klass.getConstantPool()));
			}
		}
		// methods
		for (Method m : klass.getMethods()) {
			// access flags
			buf.append(String.valueOf(m.getAccessFlags()));
			// name
			buf.append(constantValue(m.getNameIndex(), cpool));
			// signature
			buf.append(constantValue(m.getSignatureIndex(), cpool));
			// code
			Code code = m.getCode();
			if (code != null) {
				ByteSequence bytecode = new ByteSequence(code.getCode());
				buf.append(formatBytecode(bytecode, cpool));
			}
		}
		return buf.toString().getBytes(VictimsConfig.charset());
	}
}
