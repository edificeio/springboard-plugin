var gulp = require('gulp');
var sass = require('gulp-sass');
var bower = require('gulp-bower');
var del = require('del');
var autoprefixer = require('gulp-autoprefixer');
var merge = require('merge2');
var rev = require('gulp-rev');
var revReplace = require('gulp-rev-replace');
var odeSassImports = require('gulp-ode-sass-imports');

var themeConf = require('./theme-conf').conf;

var sourceDependency = [];

function widgetsSources() {
    var sources = [];
    for(var widget in themeConf.dependencies.widgets){
        sources.push('./bower_components/' + widget + '/**/*');
    }
    return sources;
}

function themesSources() {
    var sources = [];
    for(var theme in themeConf.dependencies.themes){
        sources.push('./bower_components/' + theme + '/**/*');
    }
    return sources;
}

gulp.task('clean', function(){
    var delPaths = ['./assets/themes/*', './bower_components'];
    themeConf.overriding.forEach((overriding) => {
        delPaths.push('!./assets/themes/' + overriding.child);
        delPaths.push('./assets/themes/' + overriding.child + '/*');
        delPaths.push('!./assets/themes/' + overriding.child + '/override*');
    })
    return del(delPaths);
});

gulp.task('bower', ['clean'], () => {
    return bower({ cwd: '.' });
});

gulp.task('update', ['bower'], function () {
    var themes = gulp.src(themesSources(), { base: './bower_components' })
        .pipe(gulp.dest('./assets/themes'));
    
    var widgets = gulp.src(widgetsSources(), { base: './bower_components' })
        .pipe(gulp.dest('./assets/widgets'));

    return merge([themes, widgets]);
});

gulp.task('fill-theme', sourceDependency, function () {
    var streams = [];
    themeConf.overriding.forEach((theme) => {
        streams.push(
            gulp.src('./assets/themes/' + theme.parent + '/**/*')
                .pipe(gulp.dest('./assets/themes/' + theme.child))
        );
    })
    return merge(streams);
});

gulp.task('version-fonts', ['fill-theme'], function(){
    var fonts = ['./assets/themes/generic-icons/**/*.woff', './assets/themes/generic-icons/**/*.ttf', './assets/themes/generic-icons/**/*.svg'];
    return gulp.src(fonts)
        .pipe(rev())
        .pipe(gulp.dest('./assets/themes/generic-icons'))
        .pipe(rev.manifest())
        .pipe(gulp.dest('./'));
});

gulp.task('copy-local', function () {
    var streams = [];
    for(var theme in themeConf.dependencies.themes){
        streams.push(
            gulp.src(themeConf.dependencies.themes[theme], {base: '../'})
                .pipe(gulp.dest('./assets/themes'))
        )
    }

    for(var widget in themeConf.dependencies.widgets){
        streams.push(
            gulp.src(themeConf.dependencies.widgets[widget], {base: '../'})
                .pipe(gulp.dest('./assets/widgets'))
        )
    }
    
    return merge(streams);
});

gulp.task('override-theme', ['version-fonts'], function () {
    var overrides = ['img', 'js', 'fonts', 'template', 'skins', 'i18n'];
    var streams = [];
    themeConf.overriding.forEach((overriding) => {
        overrides.forEach((override) => {
            streams.push(
                gulp.src(['./assets/themes/' + overriding.child + '/overrides/' + override + '/**/*'])
                    .pipe(gulp.dest('./assets/themes/' + overriding.child + '/' + override))
            );
        });

        streams.push(
            gulp.src(['./assets/themes/' + overriding.parent + '/css/modules/_modules.scss'])
                .pipe(odeSassImports(overriding.parent))
                .pipe(gulp.dest('./assets/themes/' + overriding.parent + '/css/modules'))
        );

        streams.push(
            gulp.src(['./assets/themes/' + overriding.child + '/css/modules/_modules.scss'])
                .pipe(odeSassImports(overriding.parent))
                .pipe(gulp.dest('./assets/themes/' + overriding.child + '/css/modules'))
        );
    })
    
    return merge(streams);
});

gulp.task('compile-sass', ['override-theme'], function () {
    var streams = [];

    themeConf.overriding.forEach((overriding) => {
        overriding.skins.forEach((skin) => {
            streams.push(
                gulp.src('./assets/themes/' + overriding.child + '/skins/' + skin + '/theme.scss')
                    .pipe(sass({ outputStyle: 'compressed' }))
                    .pipe(autoprefixer())
                    .pipe(revReplace({manifest: gulp.src("./rev-manifest.json") }))
                    .pipe(gulp.dest('./assets/themes/' + overriding.child + '/skins/' + skin))
            );
        })
    });

    return merge(streams);
});


gulp.task('build-local', function(){
    sourceDependency.push('copy-local')
    return gulp.start('compile-sass')
});
gulp.task('build', function(){
    sourceDependency.push('update');
    return gulp.start('compile-sass');
});