package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import rx.Observable

interface LoginSource : Source, ConfigurableSource {

    fun isLogged(): Boolean

    fun getUserName() : String

    fun getPassword() : String

    fun setUserNameAndPassword(username: String, password: String)

    fun login(username: String, password: String): Observable<Boolean>

}