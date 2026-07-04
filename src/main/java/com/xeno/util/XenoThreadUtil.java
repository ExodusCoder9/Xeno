package com.xeno.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class XenoThreadUtil {
	public static ThreadFactory createThreadFactory(String name, boolean daemon) {
		AtomicInteger counter = new AtomicInteger(0);
		return r -> {
			Thread t = new Thread(r, name + "-" + counter.getAndIncrement());
			t.setDaemon(daemon);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		};
	}
}
