package com.dpashko.krender

/**

The interface for application lifecycle listeners.
 */
interface ApplicationListener {

    /**

    Called when the application is first created.
     */
    fun create()

    /**

    Called every frame to update and render the application.
     */
    fun render()

    /**

    Called when the application is paused, such as when the user switches to a different app.
     */
    fun pause()

    /**

    Called when the application is resumed, such as when the user returns to the app or screen.
     */
    fun resume()

    /**

    Called when the application is about to be disposed, such as when the user closes the app.
     */
    fun dispose()
}