package com.mongodb.realm.realmkmmapp

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmObject
import org.mongodb.kbson.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// :replace-start: {
//    "terms": {
//       "UpdateTest_": "",
//       "CRUDTest.": "",
//       "frogObjectId": "ObjectId()"
//    }
// }

class UpdateTest_Frog : RealmObject {
    var _id: ObjectId = ObjectId()
    var name: String = ""
    var favoritePondsByForest: RealmDictionary<String> = realmDictionaryOf()
}

class UpdateTest: RealmTest() {
    @Test
    fun updateRealmDictionaryType() {
        runBlocking {
            val config = RealmConfiguration.Builder(
                schema = setOf(UpdateTest_Frog::class) // Pass the defined class as the object schema
            )
                .directory("/tmp/") // default location for jvm is... in the project root
                .build()
            val realm = Realm.open(config)
            Log.v("Successfully opened realm: ${realm.configuration.name}")

            // Delete frogs to make this test successful on consecutive reruns
            realm.write {
                // fetch all frogs from the realm
                val frogs: RealmResults<UpdateTest_Frog> = this.query<UpdateTest_Frog>().find()
                // call delete on the results of a query to delete those objects permanently
                delete(frogs)
                assertEquals(0, frogs.size)
            }

            // Create an object with a dictionary property to set up the test
            realm.write {
                this.copyToRealm(UpdateTest_Frog().apply {
                    name = "Kermit"
                    favoritePondsByForest = realmDictionaryOf("Hundred Acre Wood" to "Picnic Pond", "Lothlorien" to "Linya")
                })
            }
            // :snippet-start: update-realm-dictionary
            // Find frogs who have forests with favorite ponds
            val frogs = realm.query<UpdateTest_Frog>().find()
            val frogsWithFavoritePonds = frogs.query("favoritePondsByForest.@count > 1").find()
            val thisFrog = frogsWithFavoritePonds.first()
            // :remove-start:
            assertEquals(2, thisFrog.favoritePondsByForest.size)
            assertTrue(thisFrog.favoritePondsByForest.containsKey("Hundred Acre Wood"))
            // :remove-end:
            // Update the value for a key if it exists
            if (thisFrog.favoritePondsByForest.containsKey("Hundred Acre Wood")) {
                realm.write {
                    findLatest(thisFrog)?.favoritePondsByForest?.set(
                        "Hundred Acre Wood",
                        "Lily Pad Pond"
                    )
                }
            }
            // Add a new key-value pair
            realm.write {
                findLatest(thisFrog)?.favoritePondsByForest?.put("Sherwood Forest", "Miller Pond")
            }
            // :snippet-end:
            val thisFrogUpdated = realm.query<UpdateTest_Frog>("favoritePondsByForest.@count > 1").find().first()
            assertTrue(thisFrogUpdated.favoritePondsByForest.containsValue("Lily Pad Pond"))
            assertTrue(thisFrogUpdated.favoritePondsByForest.containsKey("Sherwood Forest"))
            assertTrue(thisFrogUpdated.favoritePondsByForest["Sherwood Forest"] == "Miller Pond")
            realm.close()
        }
    }

    @Test
    fun upsertAnObjectTest() {
        val REALM_NAME = getRandom()

        runBlocking {
            val config = RealmConfiguration.Builder(setOf(CRUDTest.Frog::class, Sample::class))
                .name(REALM_NAME)
                .directory(TMP_PATH)
                .build()
            val realm = Realm.open(config)
            Log.v("Successfully opened realm: ${realm.configuration.name}")
            // insert an object that meets our example query
            val frogObjectId = ObjectId()
            realm.writeBlocking {
                // Remove any existing matching frogs to start fresh
                val frogs: RealmResults<CRUDTest.Frog> =
                    this.query<CRUDTest.Frog>("name == 'Wirt'").find()
                delete(frogs)
                this.copyToRealm(CRUDTest.Frog().apply {
                    _id = frogObjectId
                    name = "Wirt"
                    age = 45
                    species = "Green"
                    owner = "Jim"
                })
            }
            val frogsMatchingId: RealmResults<CRUDTest.Frog> =
                realm.query<CRUDTest.Frog>("_id == $0", frogObjectId).find()
            assertEquals(1, frogsMatchingId.count())

            // :snippet-start: upsert-an-object
            realm.write {
                // The ID of a particular frog can either already exist or be a new ObjectId
                val frogId = frogObjectId
                // If a frog matching the ID exists, update its properties, otherwise create it
                this.copyToRealm(CRUDTest.Frog().apply {
                    _id = frogId
                    name = "Wirt"
                    age = 4
                    species = "Greyfrog"
                    owner = "L'oric"
                }, updatePolicy = UpdatePolicy.ALL)
            }
            // :snippet-end:
            val updatedFrogsMatchingId: RealmResults<CRUDTest.Frog> =
                realm.query<CRUDTest.Frog>("_id == $0", frogObjectId).find()
            assertEquals(1, updatedFrogsMatchingId.count())
            assertEquals(4, updatedFrogsMatchingId.first().age)
            assertEquals("Greyfrog", updatedFrogsMatchingId.first().species)
            assertEquals("L'oric", updatedFrogsMatchingId.first().owner)
            realm.close()
        }
    }
}

// :replace-end: