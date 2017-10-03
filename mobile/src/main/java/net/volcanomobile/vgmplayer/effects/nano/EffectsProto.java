// Generated by the protocol buffer compiler.  DO NOT EDIT!

package net.volcanomobile.vgmplayer.effects.nano;

@SuppressWarnings("hiding")
public interface EffectsProto {

  public static final class BassBoostSettings extends
      com.google.protobuf.nano.MessageNano {

    private static volatile BassBoostSettings[] _emptyArray;
    public static BassBoostSettings[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new BassBoostSettings[0];
          }
        }
      }
      return _emptyArray;
    }

    // bool enabled = 1;
    public boolean enabled;

    // int32 strength = 2;
    public int strength;

    public BassBoostSettings() {
      clear();
    }

    public BassBoostSettings clear() {
      enabled = false;
      strength = 0;
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (this.enabled != false) {
        output.writeBool(1, this.enabled);
      }
      if (this.strength != 0) {
        output.writeInt32(2, this.strength);
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (this.enabled != false) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeBoolSize(1, this.enabled);
      }
      if (this.strength != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeInt32Size(2, this.strength);
      }
      return size;
    }

    @Override
    public BassBoostSettings mergeFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      while (true) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            return this;
          default: {
            if (!com.google.protobuf.nano.WireFormatNano.parseUnknownField(input, tag)) {
              return this;
            }
            break;
          }
          case 8: {
            this.enabled = input.readBool();
            break;
          }
          case 16: {
            this.strength = input.readInt32();
            break;
          }
        }
      }
    }

    public static BassBoostSettings parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new BassBoostSettings(), data);
    }

    public static BassBoostSettings parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new BassBoostSettings().mergeFrom(input);
    }
  }

  public static final class EqualizerSettings extends
      com.google.protobuf.nano.MessageNano {

    private static volatile EqualizerSettings[] _emptyArray;
    public static EqualizerSettings[] emptyArray() {
      // Lazily initializes the empty array
      if (_emptyArray == null) {
        synchronized (
            com.google.protobuf.nano.InternalNano.LAZY_INIT_LOCK) {
          if (_emptyArray == null) {
            _emptyArray = new EqualizerSettings[0];
          }
        }
      }
      return _emptyArray;
    }

    // bool enabled = 1;
    public boolean enabled;

    // sint32 curPreset = 2;
    public int curPreset;

    // int32 numBands = 3;
    public int numBands;

    // repeated sint32 bandValues = 4;
    public int[] bandValues;

    public EqualizerSettings() {
      clear();
    }

    public EqualizerSettings clear() {
      enabled = false;
      curPreset = 0;
      numBands = 0;
      bandValues = com.google.protobuf.nano.WireFormatNano.EMPTY_INT_ARRAY;
      cachedSize = -1;
      return this;
    }

    @Override
    public void writeTo(com.google.protobuf.nano.CodedOutputByteBufferNano output)
        throws java.io.IOException {
      if (this.enabled != false) {
        output.writeBool(1, this.enabled);
      }
      if (this.curPreset != 0) {
        output.writeSInt32(2, this.curPreset);
      }
      if (this.numBands != 0) {
        output.writeInt32(3, this.numBands);
      }
      if (this.bandValues != null && this.bandValues.length > 0) {
        for (int i = 0; i < this.bandValues.length; i++) {
          output.writeSInt32(4, this.bandValues[i]);
        }
      }
      super.writeTo(output);
    }

    @Override
    protected int computeSerializedSize() {
      int size = super.computeSerializedSize();
      if (this.enabled != false) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeBoolSize(1, this.enabled);
      }
      if (this.curPreset != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeSInt32Size(2, this.curPreset);
      }
      if (this.numBands != 0) {
        size += com.google.protobuf.nano.CodedOutputByteBufferNano
            .computeInt32Size(3, this.numBands);
      }
      if (this.bandValues != null && this.bandValues.length > 0) {
        int dataSize = 0;
        for (int i = 0; i < this.bandValues.length; i++) {
          int element = this.bandValues[i];
          dataSize += com.google.protobuf.nano.CodedOutputByteBufferNano
              .computeSInt32SizeNoTag(element);
        }
        size += dataSize;
        size += 1 * this.bandValues.length;
      }
      return size;
    }

    @Override
    public EqualizerSettings mergeFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      while (true) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            return this;
          default: {
            if (!com.google.protobuf.nano.WireFormatNano.parseUnknownField(input, tag)) {
              return this;
            }
            break;
          }
          case 8: {
            this.enabled = input.readBool();
            break;
          }
          case 16: {
            this.curPreset = input.readSInt32();
            break;
          }
          case 24: {
            this.numBands = input.readInt32();
            break;
          }
          case 32: {
            int arrayLength = com.google.protobuf.nano.WireFormatNano
                .getRepeatedFieldArrayLength(input, 32);
            int i = this.bandValues == null ? 0 : this.bandValues.length;
            int[] newArray = new int[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.bandValues, 0, newArray, 0, i);
            }
            for (; i < newArray.length - 1; i++) {
              newArray[i] = input.readSInt32();
              input.readTag();
            }
            // Last one without readTag.
            newArray[i] = input.readSInt32();
            this.bandValues = newArray;
            break;
          }
          case 34: {
            int length = input.readRawVarint32();
            int limit = input.pushLimit(length);
            // First pass to compute array length.
            int arrayLength = 0;
            int startPos = input.getPosition();
            while (input.getBytesUntilLimit() > 0) {
              input.readSInt32();
              arrayLength++;
            }
            input.rewindToPosition(startPos);
            int i = this.bandValues == null ? 0 : this.bandValues.length;
            int[] newArray = new int[i + arrayLength];
            if (i != 0) {
              java.lang.System.arraycopy(this.bandValues, 0, newArray, 0, i);
            }
            for (; i < newArray.length; i++) {
              newArray[i] = input.readSInt32();
            }
            this.bandValues = newArray;
            input.popLimit(limit);
            break;
          }
        }
      }
    }

    public static EqualizerSettings parseFrom(byte[] data)
        throws com.google.protobuf.nano.InvalidProtocolBufferNanoException {
      return com.google.protobuf.nano.MessageNano.mergeFrom(new EqualizerSettings(), data);
    }

    public static EqualizerSettings parseFrom(
            com.google.protobuf.nano.CodedInputByteBufferNano input)
        throws java.io.IOException {
      return new EqualizerSettings().mergeFrom(input);
    }
  }
}
