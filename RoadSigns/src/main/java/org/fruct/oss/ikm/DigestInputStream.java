package org.fruct.oss.ikm;

import org.fruct.oss.ikm.utils.Utils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestInputStream extends FilterInputStream {
	private String algorithm;
	private String expectedHash;
	private MessageDigest digest;

	public DigestInputStream(InputStream input, String algorithm, String expectedHash) throws NoSuchAlgorithmException {
		super(input);
		this.algorithm = algorithm;
		this.expectedHash = expectedHash;

		digest = MessageDigest.getInstance(algorithm);
	}



	@Override
	public int read() throws IOException {
		final int c = super.read();
		if (c != -1)
			digest.update((byte) c);
		else
			validate();
		return c;
	}

	private void validate() throws IOException {
		String hash = Utils.toHex(digest.digest());
		if (!hash.equals(expectedHash))
			throw new IOException("Data corrupted hash " + hash + " expected " + expectedHash);

	}

	@Override
	public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
		final int length = super.read(buffer, byteOffset, byteCount);

		if (length > 0)
			digest.update(buffer, byteOffset, length);

		if (length == -1)
			validate();

		return length;
	}
}
