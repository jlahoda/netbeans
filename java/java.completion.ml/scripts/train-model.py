from __future__ import absolute_import, division, print_function, unicode_literals

import tensorflow as tf
from tensorflow import keras

import tensorflowjs as tfjs

import numpy as np

train_data = np.loadtxt('train_data.txt')
train_labels = np.loadtxt('train_labels.txt')
test_data = np.loadtxt('test_data.txt')
test_labels = np.loadtxt('test_labels.txt')

print("Training entries: {}, labels: {}".format(len(train_data), len(train_labels)))

#TODO: read the vocabulary (size) from a file:
vocab_size = 157364

model = keras.Sequential()
model.add(keras.layers.Embedding(vocab_size, 16, name="input"))
model.add(keras.layers.GlobalAveragePooling1D())
#model.add(keras.layers.Dense(256, activation=tf.nn.relu))
#model.add(keras.layers.Dense(128, activation=tf.nn.relu))
model.add(keras.layers.Dense(64, activation=tf.nn.relu))
model.add(keras.layers.Dense(len(train_labels[0]), name="output"))
#model.add(keras.layers.Dense(1))

model.summary()

model.compile(optimizer='adam',
              loss='binary_crossentropy',
              metrics=['acc'])

history = model.fit(train_data,
                    train_labels,
#                    epochs=40,
                    epochs=100,
                    batch_size=512,
                    validation_split=0.2,
                    verbose=1)

test_predictions = model.predict(train_data);

np.savetxt('results-train.txt', test_predictions)

test_predictions = model.predict(test_data);

np.savetxt('results-test.txt', test_predictions)

print(model.predict([[[262, 3, 4, 0, 152, 153, 3, 199, 200, 3]]]))

tfjs.converters.save_keras_model(model, "/tmp/tjs")
