/*
 Copyright (C) 2016 Electronic Arts Inc.  All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1.  Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
 2.  Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
 3.  Neither the name of Electronic Arts, Inc. ("EA") nor the names of
     its contributors may be used to endorse or promote products derived
     from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY ELECTRONIC ARTS AND ITS CONTRIBUTORS "AS IS" AND ANY
 EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL ELECTRONIC ARTS OR ITS CONTRIBUTORS BE LIABLE FOR ANY
 DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package cloud.orbit.actors.cloner;

import cloud.orbit.actors.ActorObserver;
import cloud.orbit.actors.runtime.AbstractActor;
import cloud.orbit.actors.runtime.ActorRuntime;
import cloud.orbit.actors.runtime.DefaultDescriptorFactory;
import cloud.orbit.actors.runtime.RemoteReference;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.esotericsoftware.kryo.serializers.CollectionSerializer;
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import com.esotericsoftware.kryo.serializers.MapSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Kyro based object cloning implementation.
 */
public class KryoCloner implements ExecutionObjectCloner
{
    private final KryoPool kryoPool;

    public KryoCloner()
    {
        KryoFactory factory = new KryoFactory()
        {
            public Kryo create()
            {
                Kryo kryo = new Kryo();

                // Adding support to clone unmodifiable collections as modifiable
                // collections.

                // It has to be noted that this might not be the expected behavior
                // in some cases.

                // Without this the unmodifiable collections cause kryo to throw an
                // exception.. Class cannot be created (missing no-arg constructor).

                kryo.addDefaultSerializer(Collection.class, new CollectionSerializer()
                {
                    final Class<?> unmodifiableCollectionClazz = Collections.unmodifiableCollection(new ArrayList()).getClass();
                    final Class<?> unmodifiableListClazz = Collections.unmodifiableList(new ArrayList()).getClass();
                    final Class<?> unmodifiableSetClazz = Collections.unmodifiableSet(new HashSet()).getClass();

                    @Override
                    protected Collection createCopy(Kryo kryo, Collection original)
                    {
                        if (original.getClass() == unmodifiableListClazz || original.getClass() == unmodifiableCollectionClazz)
                        {
                            return new ArrayList();
                        }
                        if (original.getClass() == unmodifiableSetClazz)
                        {
                            return new LinkedHashSet();
                        }
                        return super.createCopy(kryo, original);
                    }
                });
                kryo.addDefaultSerializer(Map.class, new MapSerializer()
                {
                    final Class<?> unmodifiableMapClazz = Collections.unmodifiableMap(new HashMap()).getClass();

                    @Override
                    protected Map createCopy(Kryo kryo, Map original)
                    {
                        if (original.getClass() == unmodifiableMapClazz)
                        {
                            return new LinkedHashMap<>();
                        }
                        return super.createCopy(kryo, original);
                    }
                });

                kryo.addDefaultSerializer(RemoteReference.class, new DefaultSerializers.VoidSerializer()
                {
                    @Override
                    public Object copy(final Kryo kryo, final Object original)
                    {
                        if (original instanceof RemoteReference)
                        {
                            final RemoteReference<?> remoteReference = (RemoteReference<?>) original;
                            if (RemoteReference.getRuntime(remoteReference) != null)
                            {
                                return DefaultDescriptorFactory.get().getReference(null, RemoteReference.getAddress(remoteReference),
                                        RemoteReference.getInterfaceClass(remoteReference),
                                        RemoteReference.getId(remoteReference));
                            }
                        }
                        return original;
                    }
                });
                kryo.addDefaultSerializer(AbstractActor.class, new DefaultSerializers.VoidSerializer()
                {
                    @Override
                    public Object copy(final Kryo kryo, final Object original)
                    {
                        if (original instanceof AbstractActor)
                        {
                            return RemoteReference.from((AbstractActor) original);
                        }
                        if (original instanceof RemoteReference)
                        {

                            final RemoteReference<?> remoteReference = (RemoteReference<?>) original;
                            if (RemoteReference.getRuntime(remoteReference) != null)
                            {
                                return DefaultDescriptorFactory.get().getReference(null, RemoteReference.getAddress(remoteReference),
                                        RemoteReference.getInterfaceClass(remoteReference),
                                        RemoteReference.getId(remoteReference));
                            }
                            return original;
                        }
                        if (original == null)
                        {
                            return null;
                        }
                        throw new IllegalArgumentException("Invalid type for " + original);
                    }
                });
                kryo.addDefaultSerializer(ActorObserver.class, new DefaultSerializers.VoidSerializer()
                {
                    @Override
                    public Object copy(final Kryo kryo, final Object original)
                    {
                        if (original instanceof RemoteReference)
                        {
                            final RemoteReference<?> remoteReference = (RemoteReference<?>) original;
                            if (RemoteReference.getRuntime(remoteReference) != null)
                            {
                                return DefaultDescriptorFactory.get().getReference(null, RemoteReference.getAddress(remoteReference),
                                        RemoteReference.getInterfaceClass(remoteReference),
                                        RemoteReference.getId(remoteReference));
                            }
                            return original;
                        }
                        return ActorRuntime.getRuntime().registerObserver(null, (ActorObserver) original);
                    }
                });
                kryo.addDefaultSerializer(UUID.class, new DefaultSerializers.VoidSerializer());

                return kryo;
            }
        };

        kryoPool = new KryoPool.Builder(factory).softReferences().build();
    }

    @Override
    public <T> T clone(final T object)
    {
        if (object != null)
        {
            return kryoPool.run(kryo -> {
                try
                {
                    return kryo.copy(object);
                }
                finally
                {
                    kryo.reset();
                }
            });
        }
        return null;
    }
}
