package com.dpashko.krender.scene.common

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * The abstract base class for scene states.
 */
abstract class SceneState {

    /**
     * Abstract method to get the object to persist.
     */
    abstract fun getObjectForPersistence(): ByteArray

    /**
     * Saves the state to a byte array.
     *
     * @return a byte array representing the state
     */
    fun saveState(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use {
            it.writeObject(getObjectForPersistence())
        }
        return bytes.toByteArray()
    }

    companion object {
        /**
         * Restores the state from a byte array.
         *
         * @param bytes the byte array representing the state
         * @return the restored scene state
         */
        inline fun <reified T : SceneState> restoreState(bytes: ByteArray): T {
            return ObjectInputStream(ByteArrayInputStream(bytes)).use {
                it.readObject() as T
            }
        }
    }
}