package org.fruct.oss.ikm.utils.bind;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BindHelper {
	private static final Logger log = LoggerFactory.getLogger(BindHelper.class);

	private static class ServiceDesc {
		Field field;
		Method setter;
		Method getter;
	}


	private static class FieldDesc {
		private FieldDesc(Object obj, Class<?> service) {
			this.obj = obj;
			this.service = service;
		}

		final Object obj;
		Class<?> service;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			FieldDesc fieldDesc = (FieldDesc) o;

			if (!obj.equals(fieldDesc.obj)) return false;
			if (!service.equals(fieldDesc.service)) return false;

			return true;
		}

		@Override
		public int hashCode() {
			int result = obj.hashCode();
			result = 31 * result + service.hashCode();
			return result;
		}
	}

	private static final Map<FieldDesc, ServiceConnection> connections
			= Collections.synchronizedMap(new HashMap<FieldDesc, ServiceConnection>());

	public static void autoBind(Context context, Object obj) {
		Class<?> cls = obj.getClass();

		HashMap<Class<?>, ServiceDesc> serviceMap = new HashMap<Class<?>, ServiceDesc>();

		for (Field field : cls.getDeclaredFields()) {
			if (field.isAnnotationPresent(Bind.class)) {
				Class<?> serviceClass = field.getType();

				ServiceDesc desc = serviceMap.get(serviceClass);
				if (desc == null) {
					desc = new ServiceDesc();
					serviceMap.put(serviceClass, desc);
				}
				desc.field = field;
			}
		}

		for (Method method : cls.getDeclaredMethods()) {
			if (method.isAnnotationPresent(BindGetter.class) || method.isAnnotationPresent(BindSetter.class)) {
				Class<?>[] parameterTypes = method.getParameterTypes();
				if (parameterTypes.length != 1 || Service.class.isAssignableFrom(parameterTypes[0])) {
					ServiceDesc desc = serviceMap.get(parameterTypes[0]);
					if (desc == null) {
						desc = new ServiceDesc();
						serviceMap.put(parameterTypes[0], desc);
					}

					if (method.isAnnotationPresent(BindGetter.class)) {
						desc.getter = method;
					} else {
						desc.setter = method;
					}
 				}
			}
		}

		for (Map.Entry<Class<?>, ServiceDesc> entry : serviceMap.entrySet()) {
			ServiceDesc desc = entry.getValue();

			Intent intent = new Intent(context, entry.getKey());
			ServiceConnection conn = createServiceConnection(obj, desc.field, desc.getter, desc.setter);

			connections.put(new FieldDesc(obj, entry.getKey()), conn);
			context.bindService(intent, conn, Context.BIND_AUTO_CREATE);
		}
	}

	public static void autoUnbind(Context context, Object obj) {
		Set<Class<?>> serviceClasses = new HashSet<Class<?>>();

		Class<?> cls = obj.getClass();
		for (Field field : cls.getDeclaredFields()) {
			if (field.getAnnotation(Bind.class) != null) {
				Class<?> serviceClass = field.getType();
				serviceClasses.add(serviceClass);
			}
		}

		for (Method method : cls.getDeclaredMethods()) {
			if (method.isAnnotationPresent(BindGetter.class) || method.isAnnotationPresent(BindSetter.class)) {
				Class<?>[] parameterTypes = method.getParameterTypes();
				if (parameterTypes.length != 1 || Service.class.isAssignableFrom(parameterTypes[0])) {
					serviceClasses.add(parameterTypes[0]);
				}
			}
		}

		for (Class<?> serviceClass : serviceClasses) {
			ServiceConnection conn = connections.remove(new FieldDesc(obj, serviceClass));
			conn.onServiceDisconnected(new ComponentName(context, serviceClass));
			context.unbindService(conn);
		}
	}

	private static ServiceConnection createServiceConnection(final Object obj, final Field field, final Method getter, final Method setter) {
		return new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				BindHelperBinder binder = (BindHelperBinder) service;
				Service s = binder.getService();
				try {
					if (field != null) {
						field.set(obj, s);
					}

					if (setter != null) {
						setter.invoke(obj, s);
					}
				} catch (Exception e) {
					log.error("Can't process bind response");
				}
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				try {
					if (setter != null) {
						setter.invoke(obj, new Object[] {null});
					}

					if (field != null) {
						field.set(obj, null);
					}
				} catch (Exception e) {
					log.error("Can't process unbind response");
				}
			}
		};
	}
}
