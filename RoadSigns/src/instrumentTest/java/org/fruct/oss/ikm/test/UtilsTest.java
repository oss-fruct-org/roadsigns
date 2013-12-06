package org.fruct.oss.ikm.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.Utils;

import android.test.AndroidTestCase;

public class UtilsTest extends AndroidTestCase {
	public void testUtilNormalizeAngle() {
		assertEquals(0, Utils.normalizeAngle(360), 0.1);
		assertEquals(0, Utils.normalizeAngle(0), 0.1);
		assertEquals(90, Utils.normalizeAngle(360 + 90), 0.1);
		assertEquals(-178, Utils.normalizeAngle(182), 0.1); // TODO: emulator 4.2 error (182)
		assertEquals(-179, Utils.normalizeAngle(-179), 0.1);
		assertEquals(-1, Utils.normalizeAngle(359), 0.1);
		assertEquals(-90, Utils.normalizeAngle(270), 0.1);
		
		for (int i = -1000; i < 1000; i++) {
			float na = Utils.normalizeAngle(i);
			assertTrue("a = " + i + ", na = " + na, na <= 180 && na >= -180);
		}
	}
	
	public void testHash() {
		assertEquals("76d80224611fc919a5d54f0ff9fba446", Utils.hashString("qwe"));
		assertEquals("d41d8cd98f00b204e9800998ecf8427e", Utils.hashString(""));
	}
	
	public void testHashStream() throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream("qwertyuiopasdfghjklzxcvbnm".getBytes());
		String hash = Utils.hashStream(in);
		assertEquals("e5daaa90c369adfd156862d6df632ded", hash);
		in.close();
	}
	
	public void testSolve1() {
		double sol = Utils.solve(0, 1, 0.01, new Utils.FunctionDouble() {
			@Override
			public double apply(double x) {
				return x * x - 1;
			}
		});
		
		assertEquals(1, sol, 0.1);
	}
	
	public void testSolve2() {
		double sol = Utils.solve(-1, 1, 0.01, new Utils.FunctionDouble() {
			@Override
			public double apply(double x) {
				return Math.sin(x);
			}
		});
		
		assertEquals(0, sol, 0.1);
	}

	public void testSolve3() {
		boolean failed = false;
		try {
			Utils.solve(0.1, 1, 0.01, new Utils.FunctionDouble() {
				@Override
				public double apply(double x) {
					return Math.sin(x);
				}
			});

			fail();
		} catch (IllegalArgumentException ex) {
			failed = true;
		}
		
		assertTrue(failed);
	}
	
	public void testSolve4() {
		final int[] count = new int[1];
		double res = Utils.solve(0, 2 * Math.PI + 0.3, 0.001,
				new Utils.FunctionDouble() {
					@Override
					public double apply(double x) {
						count[0] += 1;
						return x * x * x * x + x * x * x - 4 * x * x - 2;
					}
				});
		
		assertEquals(1.719, res, 0.001);
	}

	public void testMap() {
		final List<Integer> integers = new ArrayList<Integer>() {{
			add(1);
			add(2);
			add(3);
			add(4);
		}};

		List<String> result = Utils.map(integers, new Utils.Function<String, Integer>() {
			@Override
			public String apply(Integer integer) {
				return String.valueOf(integer.intValue() * 4.0);
			}
		});

		assertEquals("4.0", result.get(0));
		assertEquals("8.0", result.get(1));
		assertEquals("12.0", result.get(2));
		assertEquals("16.0", result.get(3));
	}

	public void testMapEmpty() {
		List<String> result = Utils.map(new ArrayList<Object>(), new Utils.Function<String, Object>() {
			@Override
			public String apply(Object o) {
				assertTrue("Must not be called", false);
				return null;
			}
		});

		assertTrue(result.isEmpty());
	}

	public void testReaderToString() throws IOException {
		Reader reader = new StringReader("Hell owrld");
		String string = Utils.readerToString(reader);

		assertEquals("Hell owrld", string);
	}

	public void testReaderToStringLong() throws IOException {
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < 4096; i++) {
			builder.append("Hello world");
			builder.append(123 + i);
		}

		String inputString = builder.toString();
		Reader reader = new StringReader(inputString);

		String string = Utils.readerToString(reader);

		assertEquals(inputString, string);
	}
}
