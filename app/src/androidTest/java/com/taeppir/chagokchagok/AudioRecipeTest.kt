package com.taeppir.chagokchagok

class AudioRecipeTest : android.app.Instrumentation() {
    override fun onCreate(arguments: android.os.Bundle?) { super.onCreate(arguments);start() }
    override fun onStart() {
        val tests=listOf("instrumentAuditions" to {testInstrumentAuditionsAreShortAndDistinct()},"climateRecipe" to {testClimateChangesAffectTheRenderedMusic()},"speedFallback" to {testMissingSpeedUsesDefaultTempo()},"repeatability" to {testDeterministicRecipe()},"threeConsoleCases" to {testThreeConsoleCases()})
        var failures=0
        tests.forEachIndexed {index,(name,test) ->
            val result=android.os.Bundle().apply {putString("class",javaClass.name);putString("test",name);putInt("numtests",tests.size);putInt("current",index+1);putString("id","InstrumentationTestRunner")}
            sendStatus(1,result)
            try {test();sendStatus(0,result)} catch(e:Throwable) {failures++;result.putString("stack",e.stackTraceToString());sendStatus(-2,result)}
        }
        finish(android.app.Activity.RESULT_OK,android.os.Bundle().apply {putString("stream",if(failures==0) "OK (5 tests)" else "FAILURES: $failures")})
    }
    private fun assertTrue(value:Boolean)=check(value)
    private fun assertTrue(message:String,value:Boolean)=check(value){message}
    private fun assertFalse(value:Boolean)=check(!value)
    private fun assertFalse(message:String,value:Boolean)=check(!value){message}
    private fun assertEquals(expected:Int,actual:Int)=check(expected==actual){"$expected != $actual"}
    fun testInstrumentAuditionsAreShortAndDistinct() {
        val recipe=Arrangement(94,Genre.Pop,false)
        val solos=(0..7).map {BackingTrack.renderBackingTrack(recipe,it)}
        solos.forEach {pcm ->
            assertTrue("Audition must be a short sound",pcm.size in 24000..120000)
            assertTrue("Audition must contain sound",pcm.any {kotlin.math.abs(it.toInt())>1000})
        }
        for(i in solos.indices) for(j in 0 until i) assertFalse("Different instruments must not play the same mix",solos[i].contentEquals(solos[j]))
        assertTrue(BackingTrack.renderBackingTrack(recipe).size>solos[0].size*8)
    }
    fun testClimateChangesAffectTheRenderedMusic() {
        val base=Arrangement(94,Genre.Pop,false)
        val plain=BackingTrack.renderBackingTrack(base)
        assertFalse(plain.contentEquals(BackingTrack.renderBackingTrack(base.copy(temperatureChanges=4))))
        assertFalse(plain.contentEquals(BackingTrack.renderBackingTrack(base.copy(recirculationChanges=4))))
    }
    fun testThreeConsoleCases() {
        val outputs=mutableListOf<ShortArray>()
        val destination=java.io.File(targetContext.cacheDir,"verified-beats").apply {mkdirs()}
        targetContext.assets.list("demo-songs")!!.filter {it.endsWith(".json")}.forEach {name ->
            val job=org.json.JSONObject(targetContext.assets.open("demo-songs/$name").bufferedReader().use {it.readText()})
            val r=job.getJSONObject("recipe")
            val recipe=Arrangement(r.getInt("bpm"),Genre.valueOf(r.getString("genre")),r.getBoolean("syncopated"),
                rapMode=r.getBoolean("rapMode"),punchy=r.getBoolean("punchy"),wipers=r.getInt("wipers"),windows=r.getInt("windows"),
                doors=r.getInt("doors"),signals=r.getInt("signals"),temperatureChanges=r.optInt("temperatureChanges"),recirculationChanges=r.optInt("recirculationChanges"))
            val pcm=BackingTrack.renderBackingTrack(recipe)
            check(pcm.contentEquals(BackingTrack.renderBackingTrack(recipe)))
            check(outputs.none {it.contentEquals(pcm)});outputs.add(pcm)
            java.io.DataOutputStream(java.io.File(destination,job.getString("driveCase")+".wav").outputStream()).use {out ->
                fun i(v:Int) {out.writeInt(Integer.reverseBytes(v))}
                fun h(v:Int) {out.writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt())}
                out.writeBytes("RIFF");i(36+pcm.size*2);out.writeBytes("WAVEfmt ");i(16);h(1);h(1);i(24000);i(48000);h(2);h(16);out.writeBytes("data");i(pcm.size*2);pcm.forEach {h(it.toInt())}
            }
        }
        check(outputs.size==3)
    }
    fun testDeterministicRecipe() {
        val recipe=Arrangement(104,Genre.HipHop,true,wipers=10,windows=8,doors=2,signals=12,temperatureChanges=2,recirculationChanges=2)
        check(BackingTrack.renderBackingTrack(recipe).contentEquals(BackingTrack.renderBackingTrack(recipe)))
    }
    fun testMissingSpeedUsesDefaultTempo() {
        assertEquals(DriveProfile.DEFAULT_TEMPO,DriveProfile(0f,0).tempo)
        assertEquals(84,DriveProfile.summarize(listOf(10f,20f,30f),0)!!.tempo)
        assertEquals(104,DriveProfile.summarize(listOf(60f,72f,80f),0)!!.tempo)
    }
}
