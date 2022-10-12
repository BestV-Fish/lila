//Convert micro match records in mongo to multimatch games and apporpiate swiss/game objects)

//count swiss_pairing changes //105 on dev, 1476 on live
db.swiss_pairing.count({ mm: true });
//update swisspairing collection
db.swiss_pairing.find({ mm: true }).forEach(sp => {
  print(sp.s + ' swiss updating');
  db.swiss_pairing.update(
    { _id: sp._id },
    {
      $set: {
        mm: false,
        px: true,
        gpr: 2,
        mmids: [sp.mmid],
      },
      $unset: {
        mmid: true,
        mm: true,
      },
    }
  );
  print('updating game 1/2 ' + sp._id);
  var game1mm = `1:${sp._id}`;
  var game2mm = `2:${sp._id}`;
  db.game5.update(
    { _id: sp._id },
    {
      $set: {
        mm: game1mm,
      },
    }
  );
  print('updating game 2/2 ' + sp.mmid);
  db.game5.update(
    { _id: sp.mmid },
    {
      $set: {
        mm: game2mm,
      },
    }
  );
});

//count swiss changes
db.swiss.count({ 'settings.m': true }); //14 on dev, 54 on live
//update swiss settings collection
db.swiss.find({ 'settings.m': true }).forEach(s => {
  print('updating ' + s._id);
  db.swiss.update(
    { _id: s._id },
    {
      $set: {
        'settings.m': false,
        'settings.px': true,
        'settings.gpr': 2,
      },
      $unset: {
        'settings.m': true,
      },
    }
  );
  print(s._id + ' swiss updated');
});

///////////////////////////////////////////////
//PART 2 UNSETTNG MICRO MATCH FROM DB COLLECTIONS
///////////////////////////////////////////////

//unset micro match field in swiss post migration
db.swiss.find({ 'settings.m': false }).forEach(s => {
  db.swiss.update(
    { _id: s._id },
    {
      $unset: {
        'settings.m': true,
      },
    }
  );
});

//unset micromatch in swiss settings collection
db.swiss_pairing.find({ mm: false }).forEach(sp => {
  db.swiss_pairing.update(
    { _id: sp._id },
    {
      $unset: {
        mmid: true,
        mm: true,
      },
    }
  );
});
