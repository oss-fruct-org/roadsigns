package org.fruct.oss.ikm.utils.bind;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Looper;

import org.fruct.oss.ikm.DataService;
import org.fruct.oss.ikm.service.DirectionService;
import org.fruct.oss.ikm.storage.RemoteContentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BindHelper {
	private static final Logger log = LoggerFactory.getLogger(BindHelper.class);

	private static Map<Class<?>, Set<String>> states = new HashMap<Class<?>, Set<String>>();

	private static class ServiceDependence {
		Service serviceLoaded;
		Class<?> serviceClass;
		String[] states = {};
	}

	private static class GroupDependence {
		boolean done;
		Object object;
		Method method;
		List<ServiceDependence> serviceDependencies = new ArrayList<ServiceDependence>();
	}

	private static class SubscribedObject {
		Object object;
		List<GroupDependence> groupDependencies = new ArrayList<GroupDependence>();
		List<ServiceConnection> connections = new ArrayList<ServiceConnection>();
	}

	private static Map<Object, SubscribedObject> subscribedObjects = new HashMap<Object, SubscribedObject>();

	public static void autoBind(Context context, Object obj) {
		mainOrThrow();
		Class<?> cls = obj.getClass();

		List<GroupDependence> groupDependencies = new ArrayList<GroupDependence>();
		Set<Class<?>> bindingServices = new HashSet<Class<?>>();

		for (Method method : cls.getDeclaredMethods()) {
			if (method.isAnnotationPresent(BindSetter.class)) {
				GroupDependence groupDependence = new GroupDependence();

				Class<?>[] parameterTypes = method.getParameterTypes();
				Annotation[][] parameterAnnotations = method.getParameterAnnotations();
				for (int i = 0, parameterTypesLength = parameterTypes.length; i < parameterTypesLength; i++) {
					Class<?> parameterType = parameterTypes[i];
					if (!Service.class.isAssignableFrom(parameterType)) {
						continue;
					}

					bindingServices.add(parameterType);
					Annotation[] annotations = parameterAnnotations[i];

					ServiceDependence dependence = new ServiceDependence();
					for (Annotation annotation : annotations) {
						if (annotation instanceof State) {
							dependence.states = (((State) annotation).value());
							break;
						}
					}

					dependence.serviceClass = parameterType;
					groupDependence.serviceDependencies.add(dependence);
				}
				groupDependence.method = method;
				groupDependence.object = obj;

				groupDependencies.add(groupDependence);
			}
		}

		SubscribedObject subscribedObject = new SubscribedObject();
		subscribedObject.groupDependencies = groupDependencies;
		subscribedObject.object = obj;

		subscribedObjects.put(obj, subscribedObject);

		for (Class<?> service : bindingServices) {
			ServiceConnection conn = createServiceConnection(service, subscribedObject);
			subscribedObject.connections.add(conn);

			Intent intent = new Intent(context, service);
			context.bindService(intent, conn, 0);
		}
	}

	public static void autoUnbind(Context context, Object obj) {
		mainOrThrow();
		SubscribedObject subscribedObject = subscribedObjects.get(obj);
		if (subscribedObject != null) {
			for (ServiceConnection connection : subscribedObject.connections) {
				context.unbindService(connection);
			}
			subscribedObjects.remove(obj);
		}
	}

	public static void setServiceState(Service service, String state, boolean active) {
		mainOrThrow();
		Class<?> serviceClass = service.getClass();
		Set<String> serviceStates = states.get(serviceClass);
		if (serviceStates == null) {
			serviceStates = new HashSet<String>();
			states.put(serviceClass, serviceStates);
		}

		if (active) {
			serviceStates.add(state);

			for (SubscribedObject object : subscribedObjects.values()) {
				for (GroupDependence groupDependence : object.groupDependencies) {
					checkGroupDependence(serviceClass, groupDependence);
				}
			}
		} else {
			serviceStates.remove(state);
		}
	}

	private static void checkGroupDependence(Class<?> service, GroupDependence groupDependence) {
		mainOrThrow();
		if (groupDependence.done) {
			return;
		}

		Service[] services = new Service[groupDependence.serviceDependencies.size()];
		int c = 0;

		for (ServiceDependence dependence : groupDependence.serviceDependencies) {
			if (dependence.serviceLoaded != null) {
				services[c] = dependence.serviceLoaded;

				Set<String> serviceStates = states.get(dependence.serviceClass);
				for (String state : dependence.states) {
					if (serviceStates == null || !serviceStates.contains(state)) {
						return;
					}
				}
			} else {
				return;
			}

			c++;
		}

		groupDependence.done = true;

		try {
			groupDependence.method.invoke(groupDependence.object, services);
		} catch (IllegalAccessException e) {
			log.error("Can't invoke service receiver method", e);
		} catch (InvocationTargetException e) {
			log.error("Can't invoke service receiver method", e);
		}
	}

	private static void mainOrThrow() {
		if (Looper.getMainLooper().getThread() != Thread.currentThread())
			throw new RuntimeException("");
	}

	private static ServiceConnection createServiceConnection(final Class<?> serviceClass, final SubscribedObject subscribedObject) {
		return new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder binder) {
				if (serviceClass.equals(RemoteContentService.class)
						&& subscribedObject.object.getClass().equals(DirectionService.class)) {
					log.trace("AAA: RemoteContentService connected to DirectionService");
				}

				if (serviceClass.equals(DataService.class)
						&& subscribedObject.object.getClass().equals(DirectionService.class)) {
					log.trace("AAA: DataService connected to DirectionService");
				}


				Service service = ((BindHelperBinder) binder).getService();
				for (GroupDependence dependence : subscribedObject.groupDependencies) {
					for (ServiceDependence dep : dependence.serviceDependencies) {
						if (dep.serviceClass.equals(serviceClass)) {
							dep.serviceLoaded = service;
							break;
						}
					}

					checkGroupDependence(serviceClass, dependence);
				}
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {

			}
		};
	}
}
